package com.xs.sheepaimall.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xs.sheepaimall.common.BizException;
import com.xs.sheepaimall.common.ResultCode;
import com.xs.sheepaimall.dto.OrderCreateDTO;
import com.xs.sheepaimall.dto.OrderItemDTO;
import com.xs.sheepaimall.entity.*;
import com.xs.sheepaimall.mapper.OrderInfoMapper;
import com.xs.sheepaimall.service.*;
import com.xs.sheepaimall.vo.OrderInfoVO;
import com.xs.sheepaimall.vo.OrderItemVO;
import jakarta.annotation.Resource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 订单 Service —— 下单事务管控。
 *
 * 流程：校验商品有效性 → 原子扣减库存 → 生成订单+明细 → 清空购物车
 * 任何环节异常自动回滚全部操作。
 */
@Service
public class OrderServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements OrderService {

    @Resource
    private OrderItemService orderItemService;

    @Resource
    private SkuService skuService;

    @Resource
    private SpuService spuService;

    @Resource
    private CartService cartService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // ==================== 下单 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderInfoVO create(OrderCreateDTO dto) {
        List<OrderItemDTO> items = dto.getItems();
        if (items == null || items.isEmpty()) {
            throw new BizException("订单明细不能为空");
        }

        Long memberId = dto.getMemberId();

        // ===== 1. 校验所有商品有效性（fail-fast，不产生脏数据） =====
        List<ValidatedItem> validatedItems = new ArrayList<>();
        for (OrderItemDTO item : items) {
            validatedItems.add(validateItem(item));
        }

        // ===== 2. 原子扣减库存 =====
        for (int i = 0; i < validatedItems.size(); i++) {
            ValidatedItem vi = validatedItems.get(i);
            OrderItemDTO item = items.get(i);
            // deductStock 内部使用 WHERE stock >= quantity 原子扣减，库存不足自动抛异常
            skuService.deductStock(item.getSkuId(), item.getQuantity());
        }

        // ===== 3. 计算金额、生成订单 =====
        BigDecimal totalAmount = validatedItems.stream()
                .map(vi -> vi.sku.getPrice().multiply(BigDecimal.valueOf(vi.quantity)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        OrderInfo order = new OrderInfo();
        order.setOrderNo(generateOrderNo());
        order.setMemberId(memberId);
        order.setTotalAmount(totalAmount);
        order.setPayAmount(BigDecimal.ZERO); // 初始值，支付回调时由 PaymentService 更新
        order.setStatus(0);
        order.setReceiverName(dto.getReceiverName());
        order.setReceiverPhone(dto.getReceiverPhone());
        order.setReceiverAddress(dto.getReceiverAddress());
        order.setRemark(dto.getRemark());
        this.save(order);

        // ===== 4. 保存订单明细（价格快照） =====
        List<OrderItem> orderItems = new ArrayList<>();
        for (int i = 0; i < validatedItems.size(); i++) {
            ValidatedItem vi = validatedItems.get(i);
            OrderItemDTO item = items.get(i);

            OrderItem orderItem = new OrderItem();
            orderItem.setOrderId(order.getId());
            orderItem.setSpuId(vi.spu.getId());
            orderItem.setSkuId(vi.sku.getId());
            orderItem.setSkuName(vi.sku.getSkuName());
            orderItem.setPrice(vi.sku.getPrice());
            orderItem.setQuantity(item.getQuantity());
            orderItem.setTotalPrice(vi.sku.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            orderItem.setImage(vi.spu.getMainImage());
            orderItems.add(orderItem);
        }
        orderItemService.saveBatch(orderItems);

        // ===== 5. 更新SPU销量 =====
        for (ValidatedItem vi : validatedItems) {
            Spu updateSpu = new Spu();
            updateSpu.setId(vi.spu.getId());
            updateSpu.setSalesCount(vi.spu.getSalesCount() + vi.quantity);
            spuService.updateById(updateSpu);
        }

        // ===== 6. 清空购物车中已下单的SKU（MySQL逻辑删除 + Redis缓存清除） =====
        List<Long> orderedSkuIds = items.stream()
                .map(OrderItemDTO::getSkuId)
                .collect(Collectors.toList());
        List<Cart> cartItems = cartService.list(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getMemberId, memberId)
                .in(Cart::getSkuId, orderedSkuIds));
        if (!cartItems.isEmpty()) {
            // MySQL 逻辑删除已下单的购物车条目
            cartService.removeByIds(cartItems.stream().map(Cart::getId).collect(Collectors.toList()));
            // 清除 Redis 购物车缓存，下次查询时从 MySQL 重建（自动过滤已删除条目）
            try {
                stringRedisTemplate.delete("cart::" + memberId);
            } catch (DataAccessException ignored) {
                // Redis 不可用时忽略，MySQL 已正确
            }
        }

        // ===== 7. 组装返回VO =====
        return buildOrderVO(order, orderItems);
    }

    // ==================== 订单详情 ====================

    @Override
    public OrderInfoVO getDetailById(Long id) {
        OrderInfo order = this.getById(id);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "订单不存在");
        }
        List<OrderItem> items = orderItemService.list(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, id));
        return buildOrderVO(order, items);
    }

    // ==================== 会员订单分页 ====================

    @Override
    public Page<OrderInfo> pageByMemberId(Long memberId, int pageNum, int pageSize) {
        return this.page(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<OrderInfo>()
                        .eq(OrderInfo::getMemberId, memberId)
                        .orderByDesc(OrderInfo::getCreateTime));
    }

    // ==================== 取消订单 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderInfoVO cancel(Long orderId) {
        // 1. 查订单
        OrderInfo order = this.getById(orderId);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "订单不存在");
        }
        // 仅待支付状态可取消
        if (order.getStatus() == null || order.getStatus() != 0) {
            throw new BizException("仅待支付状态的订单可取消，当前状态：" + getStatusText(order.getStatus()));
        }

        // 2. 获取订单明细
        List<OrderItem> items = orderItemService.list(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));

        // 3. 回滚库存（原子操作）
        for (OrderItem item : items) {
            boolean restored = skuService.update(new LambdaUpdateWrapper<Sku>()
                    .eq(Sku::getId, item.getSkuId())
                    .setSql("stock = stock + " + item.getQuantity()));
            if (!restored) {
                throw new BizException("库存回滚失败 skuId=" + item.getSkuId());
            }
        }

        // 4. 回滚销量
        for (OrderItem item : items) {
            spuService.update(new LambdaUpdateWrapper<Spu>()
                    .eq(Spu::getId, item.getSpuId())
                    .setSql("sales_count = sales_count - " + item.getQuantity()));
        }

        // 5. 更新订单状态为已取消
        order.setStatus(4);
        order.setCancelTime(LocalDateTime.now());
        boolean ok = this.updateById(order);
        if (!ok) {
            throw new BizException("取消订单失败 orderId=" + orderId);
        }

        return buildOrderVO(order, items);
    }

    // ==================== 内部方法 ====================

    /** 校验单条订单明细：SPU 上架、SKU 启用、SKU 归属 SPU、库存充足 */
    private ValidatedItem validateItem(OrderItemDTO item) {
        Long spuId = item.getSpuId();
        Long skuId = item.getSkuId();
        int qty = item.getQuantity() != null ? item.getQuantity() : 1;

        if (qty <= 0) {
            throw new BizException("商品数量必须大于0");
        }

        // 校验 SPU 存在且上架
        Spu spu = spuService.getById(spuId);
        if (spu == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "商品[SPU:" + spuId + "]不存在");
        }
        if (spu.getStatus() == null || spu.getStatus() != 1) {
            throw new BizException("商品[" + spu.getName() + "]已下架");
        }

        // 校验 SKU 存在、启用、归属正确
        Sku sku = skuService.getById(skuId);
        if (sku == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "商品规格[SKU:" + skuId + "]不存在");
        }
        if (sku.getStatus() == null || sku.getStatus() != 1) {
            throw new BizException("商品规格[" + sku.getSkuName() + "]已禁用");
        }
        if (!sku.getSpuId().equals(spuId)) {
            throw new BizException("SKU[" + skuId + "]不属于SPU[" + spuId + "]");
        }
        // 库存校验（扣减前最后一次检查）
        if (sku.getStock() < qty) {
            throw new BizException("商品[" + sku.getSkuName() + "]库存不足，剩余" + sku.getStock());
        }

        ValidatedItem vi = new ValidatedItem();
        vi.spu = spu;
        vi.sku = sku;
        vi.quantity = qty;
        return vi;
    }

    /** 生成订单编号：yyyyMMddHHmmss + 6位随机数 */
    private String generateOrderNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String random = RandomUtil.randomNumbers(6);
        return timestamp + random;
    }

    // ==================== 支付状态更新（支付回调专用） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePayStatus(Long orderId, BigDecimal payAmount, Integer status, LocalDateTime payTime) {
        OrderInfo order = new OrderInfo();
        order.setId(orderId);
        order.setPayAmount(payAmount);
        order.setStatus(status);
        order.setPayTime(payTime);
        boolean ok = this.updateById(order);
        if (!ok) {
            throw new BizException("更新订单支付状态失败 orderId=" + orderId);
        }
    }

    /** 组装 OrderInfoVO */
    private OrderInfoVO buildOrderVO(OrderInfo order, List<OrderItem> items) {
        OrderInfoVO vo = new OrderInfoVO();
        BeanUtil.copyProperties(order, vo);
        vo.setStatusText(getStatusText(order.getStatus()));

        List<OrderItemVO> itemVOs = items.stream()
                .map(item -> {
                    OrderItemVO iv = new OrderItemVO();
                    BeanUtil.copyProperties(item, iv);
                    return iv;
                })
                .collect(Collectors.toList());
        vo.setItems(itemVOs);
        return vo;
    }

    /** 订单状态文本 */
    private String getStatusText(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case 0 -> "待支付";
            case 1 -> "已支付";
            case 2 -> "已发货";
            case 3 -> "已完成";
            case 4 -> "已取消";
            default -> "未知";
        };
    }

    /** 校验通过的商品条目（内部使用） */
    private static class ValidatedItem {
        Spu spu;
        Sku sku;
        int quantity;
    }
}
