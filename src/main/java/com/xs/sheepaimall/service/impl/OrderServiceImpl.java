package com.xs.sheepaimall.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xs.sheepaimall.common.BizException;
import com.xs.sheepaimall.common.RabbitMQConstants;
import com.xs.sheepaimall.common.ResultCode;
import com.xs.sheepaimall.dto.OrderCreateDTO;
import com.xs.sheepaimall.dto.OrderItemDTO;
import com.xs.sheepaimall.dto.StockDeductMessage;
import com.xs.sheepaimall.entity.*;
import com.xs.sheepaimall.mapper.OrderInfoMapper;
import com.xs.sheepaimall.security.UserContext;
import com.xs.sheepaimall.service.*;
import com.xs.sheepaimall.vo.OrderInfoVO;
import com.xs.sheepaimall.vo.OrderItemVO;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 订单 Service —— 结合 RabbitMQ 实现库存锁定/释放。
 *
 * 流程：
 * 1. 下单 → 校验 + 保存订单 → 发送库存锁定消息到 MQ（异步扣库存）
 * 2. 支付成功 → 订单状态变为已支付，锁定转为正式扣减（无需额外库存操作）
 * 3. 取消订单 → 若库存已锁定则发送释放消息到 MQ（异步还库存），若尚未锁定则直接取消
 */
@Service
public class OrderServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

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

    @Resource
    private RabbitTemplate rabbitTemplate;

    // ==================== 下单 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderInfoVO create(OrderCreateDTO dto) {
        List<OrderItemDTO> items = dto.getItems();
        if (items == null || items.isEmpty()) {
            throw new BizException("订单明细不能为空");
        }

        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException("未获取到登录用户信息");
        }

        // ===== 1. 校验商品有效性（预校验库存，fail-fast） =====
        List<ValidatedItem> validatedItems = new ArrayList<>();
        for (OrderItemDTO item : items) {
            validatedItems.add(validateItem(item));
        }

        // ===== 2. 计算金额、生成订单 =====
        BigDecimal totalAmount = validatedItems.stream()
                .map(vi -> vi.sku.getPrice().multiply(BigDecimal.valueOf(vi.quantity)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        OrderInfo order = new OrderInfo();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setTotalAmount(totalAmount);
        order.setPayAmount(BigDecimal.ZERO);
        order.setStatus(0); // 待支付
        order.setReceiverName(dto.getReceiverName());
        order.setReceiverPhone(dto.getReceiverPhone());
        order.setReceiverAddress(dto.getReceiverAddress());
        order.setRemark(dto.getRemark());
        this.save(order);

        // ===== 3. 保存订单明细（价格快照） =====
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

        // ===== 4. 清空购物车中已下单的SKU =====
        List<Long> orderedSkuIds = items.stream()
                .map(OrderItemDTO::getSkuId)
                .collect(Collectors.toList());
        List<Cart> cartItems = cartService.list(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .in(Cart::getSkuId, orderedSkuIds));
        if (!cartItems.isEmpty()) {
            cartService.removeByIds(cartItems.stream().map(Cart::getId).collect(Collectors.toList()));
            try {
                stringRedisTemplate.delete("cart::" + userId);
            } catch (DataAccessException ignored) {
            }
        }

        // ===== 5. 发送库存锁定消息到 RabbitMQ（异步锁定库存） =====
        sendStockLockMessage(order.getId(), validatedItems);

        // ===== 6. 组装VO =====
        return buildOrderVO(order, orderItems);
    }

    // ==================== 订单详情 ====================

    @Override
    public OrderInfoVO getDetailById(Long id) {
        OrderInfo order = this.getById(id);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "订单不存在");
        }
        checkOrderOwnership(order, "order:list");
        List<OrderItem> items = orderItemService.list(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, id));
        return buildOrderVO(order, items);
    }

    // ==================== 会员订单查询 ====================

    @Override
    public Page<OrderInfoVO> pageByUserId(Long userId, Integer status, int pageNum, int pageSize) {
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getUserId, userId)
                .orderByDesc(OrderInfo::getCreateTime);
        if (status != null) {
            wrapper.eq(OrderInfo::getStatus, status);
        }

        Page<OrderInfo> page = this.page(new Page<>(pageNum, pageSize), wrapper);

        List<OrderInfo> orders = page.getRecords();
        if (orders.isEmpty()) {
            return new Page<OrderInfoVO>(pageNum, pageSize).setTotal(page.getTotal());
        }

        // 批量查询订单明细
        List<Long> orderIds = orders.stream().map(OrderInfo::getId).collect(Collectors.toList());
        List<OrderItem> allItems = orderItemService.list(
                new LambdaQueryWrapper<OrderItem>().in(OrderItem::getOrderId, orderIds));
        Map<Long, List<OrderItem>> itemMap = allItems.stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));

        // 批量查询 SPU 名称
        Set<Long> spuIds = allItems.stream().map(OrderItem::getSpuId).collect(Collectors.toSet());
        Map<Long, String> spuNameMap = spuService.listByIds(spuIds).stream()
                .collect(Collectors.toMap(Spu::getId, Spu::getName, (a, b) -> a));

        List<OrderInfoVO> voList = orders.stream()
                .map(order -> toOrderVO(order, itemMap.getOrDefault(order.getId(), List.of()), spuNameMap))
                .collect(Collectors.toList());

        Page<OrderInfoVO> result = new Page<>(pageNum, pageSize);
        result.setTotal(page.getTotal());
        result.setRecords(voList);
        return result;
    }

    @Override
    public List<OrderInfo> listByUserId(Long userId) {
        return this.list(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getUserId, userId)
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
        if (order.getStatus() == null || order.getStatus() != 0) {
            throw new BizException("仅待支付状态的订单可取消，当前状态：" + getStatusText(order.getStatus()));
        }

        // 校验归属：普通用户只能取消自己的订单，有 order:cancel 权限可取消任意订单
        checkOrderOwnership(order, "order:cancel");

        // 2. 获取订单明细
        List<OrderItem> items = orderItemService.list(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));

        // 3. 检查库存锁定状态，若已锁定则释放
        String lockStatusKey = RabbitMQConstants.STOCK_LOCKED_STATUS_PREFIX + orderId;
        String lockStatus = stringRedisTemplate.opsForValue().get(lockStatusKey);

        if ("LOCKED".equals(lockStatus)) {
            // 库存已被锁定 → 发送释放消息到 MQ
            sendStockReleaseMessage(orderId, items);
            stringRedisTemplate.delete(lockStatusKey);
            log.info("库存已锁定，发送释放消息 orderId={}", orderId);
        } else {
            // 尚未锁定或锁定消息尚未消费 → 无需回滚库存
            // 锁定消费者会检查订单状态，发现已取消则跳过
            log.info("库存尚未锁定，直接取消订单 orderId={}", orderId);
        }

        // 4. 更新订单状态为已取消
        order.setStatus(4);
        order.setCancelTime(LocalDateTime.now());
        boolean ok = this.updateById(order);
        if (!ok) {
            throw new BizException("取消订单失败 orderId=" + orderId);
        }

        return buildOrderVO(order, items);
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
        // 支付成功，清除锁定状态记录（锁定已转为正式扣减）
        if (status != null && status == 1) {
            stringRedisTemplate.delete(RabbitMQConstants.STOCK_LOCKED_STATUS_PREFIX + orderId);
            log.info("支付成功，清除库存锁定记录 orderId={}", orderId);
        }
    }

    // ==================== 内部方法 ====================

    /** 校验单条订单明细：SPU 上架、SKU 启用、归属正确、库存预检 */
    private ValidatedItem validateItem(OrderItemDTO item) {
        Long spuId = item.getSpuId();
        Long skuId = item.getSkuId();
        int qty = item.getQuantity() != null ? item.getQuantity() : 1;

        if (qty <= 0) {
            throw new BizException("商品数量必须大于0");
        }

        Spu spu = spuService.getById(spuId);
        if (spu == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "商品[SPU:" + spuId + "]不存在");
        }
        if (spu.getStatus() == null || spu.getStatus() != 1) {
            throw new BizException("商品[" + spu.getName() + "]已下架");
        }

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
        // 预校验库存（锁定消费者会做最终原子判断）
        if (sku.getStock() < qty) {
            throw new BizException("商品[" + sku.getSkuName() + "]库存不足，剩余" + sku.getStock());
        }

        ValidatedItem vi = new ValidatedItem();
        vi.spu = spu;
        vi.sku = sku;
        vi.quantity = qty;
        return vi;
    }

    /** 发送库存锁定消息到 MQ */
    private void sendStockLockMessage(Long orderId, List<ValidatedItem> validatedItems) {
        StockDeductMessage message = buildStockMessage(orderId, validatedItems);
        CorrelationData correlationData = new CorrelationData(message.getMessageId());

        rabbitTemplate.convertAndSend(
                RabbitMQConstants.STOCK_EXCHANGE,
                RabbitMQConstants.STOCK_LOCK_ROUTING_KEY,
                message,
                correlationData);

        log.info("库存锁定消息已发送 orderId={} messageId={}", orderId, message.getMessageId());
    }

    /** 发送库存释放消息到 MQ（取消订单时调用） */
    private void sendStockReleaseMessage(Long orderId, List<OrderItem> items) {
        StockDeductMessage message = new StockDeductMessage();
        message.setMessageId(UUID.randomUUID().toString());
        message.setOrderId(orderId);
        message.setCreateTime(LocalDateTime.now());

        List<StockDeductMessage.StockDeductItem> msgItems = items.stream()
                .map(it -> {
                    StockDeductMessage.StockDeductItem di = new StockDeductMessage.StockDeductItem();
                    di.setSpuId(it.getSpuId());
                    di.setSkuId(it.getSkuId());
                    di.setQuantity(it.getQuantity());
                    return di;
                })
                .collect(Collectors.toList());
        message.setItems(msgItems);

        CorrelationData correlationData = new CorrelationData(message.getMessageId());
        rabbitTemplate.convertAndSend(
                RabbitMQConstants.STOCK_EXCHANGE,
                RabbitMQConstants.STOCK_RELEASE_ROUTING_KEY,
                message,
                correlationData);

        log.info("库存释放消息已发送 orderId={} messageId={}", orderId, message.getMessageId());
    }

    /** 构建 StockDeductMessage */
    private StockDeductMessage buildStockMessage(Long orderId, List<ValidatedItem> validatedItems) {
        StockDeductMessage message = new StockDeductMessage();
        message.setMessageId(UUID.randomUUID().toString());
        message.setOrderId(orderId);
        message.setCreateTime(LocalDateTime.now());

        List<StockDeductMessage.StockDeductItem> msgItems = validatedItems.stream()
                .map(vi -> {
                    StockDeductMessage.StockDeductItem di = new StockDeductMessage.StockDeductItem();
                    di.setSpuId(vi.spu.getId());
                    di.setSkuId(vi.sku.getId());
                    di.setQuantity(vi.quantity);
                    return di;
                })
                .collect(Collectors.toList());
        message.setItems(msgItems);
        return message;
    }

    /** 生成订单编号：yyyyMMddHHmmss + 6位随机数 */
    private String generateOrderNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String random = RandomUtil.randomNumbers(6);
        return timestamp + random;
    }

    /** 组装 OrderInfoVO（含 SPU 名称批量查询） */
    private OrderInfoVO buildOrderVO(OrderInfo order, List<OrderItem> items) {
        OrderInfoVO vo = new OrderInfoVO();
        BeanUtil.copyProperties(order, vo);
        vo.setStatusText(getStatusText(order.getStatus()));

        // 批量查询 SPU 名称
        Set<Long> spuIds = items.stream().map(OrderItem::getSpuId).collect(Collectors.toSet());
        Map<Long, String> spuNameMap = spuService.listByIds(spuIds).stream()
                .collect(Collectors.toMap(Spu::getId, Spu::getName, (a, b) -> a));

        List<OrderItemVO> itemVOs = items.stream()
                .map(item -> {
                    OrderItemVO iv = new OrderItemVO();
                    BeanUtil.copyProperties(item, iv);
                    iv.setSpuName(spuNameMap.get(item.getSpuId()));
                    return iv;
                })
                .collect(Collectors.toList());
        vo.setItems(itemVOs);
        return vo;
    }

    /** 组装 OrderInfoVO（复用已有 SPU 名称映射，避免重复查库） */
    private OrderInfoVO toOrderVO(OrderInfo order, List<OrderItem> items, Map<Long, String> spuNameMap) {
        OrderInfoVO vo = new OrderInfoVO();
        BeanUtil.copyProperties(order, vo);
        vo.setStatusText(getStatusText(order.getStatus()));

        List<OrderItemVO> itemVOs = items.stream()
                .map(item -> {
                    OrderItemVO iv = new OrderItemVO();
                    BeanUtil.copyProperties(item, iv);
                    iv.setSpuName(spuNameMap.get(item.getSpuId()));
                    return iv;
                })
                .collect(Collectors.toList());
        vo.setItems(itemVOs);
        return vo;
    }

    /** 校验订单归属：有指定权限者可操作任意订单，否则只能操作自己的订单 */
    private void checkOrderOwnership(OrderInfo order, String requiredPermission) {
        Long currentUserId = UserContext.getUserId();
        List<String> permissions = UserContext.getPermissions();
        // 拥有管理权限 → 可操作任意用户订单
        if (permissions != null && permissions.contains(requiredPermission)) {
            return;
        }
        // 普通用户 → 只能操作自己的订单
        if (!order.getUserId().equals(currentUserId)) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权操作此订单");
        }
    }

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
