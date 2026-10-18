package com.xs.sheepaimall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.hutool.core.bean.BeanUtil;
import com.xs.sheepaimall.common.BizException;
import com.xs.sheepaimall.entity.AfterSale;
import com.xs.sheepaimall.entity.OrderInfo;
import com.xs.sheepaimall.entity.OrderItem;
import com.xs.sheepaimall.entity.Spu;
import com.xs.sheepaimall.mapper.AfterSaleMapper;
import com.xs.sheepaimall.service.AfterSaleService;
import com.xs.sheepaimall.service.FundService;
import com.xs.sheepaimall.service.OrderItemService;
import com.xs.sheepaimall.service.OrderService;
import com.xs.sheepaimall.service.PaymentService;
import com.xs.sheepaimall.service.SpuService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 售后单服务实现。
 * <p>申请校验（D2）：订单归属当前用户；类型按订单状态收敛（待发货=仅退款，待收货/已完成=退货退款/换货）；
 * 同订单无待处理售后单；商家ID从订单项反查（order_info 无商家字段），一单必须单一商家。</p>
 * <p>同意退款类（D3）：事务内执行 退款 → 订单标记已退款 → 商家退款扣回流水 → 售后单完结，失败整体回滚可重试。</p>
 */
@Slf4j
@Service
public class AfterSaleServiceImpl implements AfterSaleService {

    @Autowired
    private AfterSaleMapper afterSaleMapper;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderItemService orderItemService;

    @Autowired
    private SpuService spuService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    /** 售后申请并发锁前缀（同订单同时只放行一个申请流程） */
    private static final String AS_LOCK_PREFIX = "cs:as:lock:";

    @Autowired
    private FundService fundService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AfterSale apply(Long userId, Long orderId, String type, String reason) {
        // 并发防重：同订单同时只放行一个申请流程（两个确认单并发通过校验会双插售后单）。
        // 锁在事务提交后释放，回滚时立即释放；TTL 30s 兜底防死锁
        String lockKey = AS_LOCK_PREFIX + orderId;
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", java.time.Duration.ofSeconds(30));
        if (!Boolean.TRUE.equals(locked)) {
            throw new BizException("该订单售后申请正在提交中，请稍后再试");
        }
        try {
            if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
                org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                        new org.springframework.transaction.support.TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                stringRedisTemplate.delete(lockKey);
                            }

                            @Override
                            public void afterCompletion(int status) {
                                if (status != STATUS_COMMITTED) {
                                    stringRedisTemplate.delete(lockKey);
                                }
                            }
                        });
            }
            OrderInfo order = validate(userId, orderId, type);
            Long merchantId = resolveMerchantId(orderId);
            if (merchantId == null) {
                throw new BizException("订单归属商家不明确，请转人工处理");
            }

            AfterSale afterSale = new AfterSale();
            afterSale.setOrderId(orderId);
            afterSale.setOrderNo(order.getOrderNo());
            afterSale.setUserId(userId);
            afterSale.setMerchantId(merchantId);
            afterSale.setType(type);
            afterSale.setReason(reason);
            afterSale.setStatus(STATUS_PENDING);
            afterSale.setRefundAmount(order.getPayAmount() != null ? order.getPayAmount() : order.getTotalAmount());
            afterSaleMapper.insert(afterSale);
            log.info("售后申请创建 afterSaleId={} orderId={} type={} userId={}", afterSale.getId(), orderId, type, userId);
            return afterSale;
        } catch (RuntimeException e) {
            // 事务将回滚：立即释放锁，允许用户重试
            stringRedisTemplate.delete(lockKey);
            throw e;
        }
    }

    @Override
    public OrderInfo validate(Long userId, Long orderId, String type) {
        OrderInfo order = orderService.getById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BizException("订单不存在或不属于当前用户");
        }
        // 类型按订单状态收敛（0待支付 1已支付(待发货) 2已发货(待收货) 3已完成 4已取消）
        Integer status = order.getStatus();
        boolean allowRefundOnly = TYPE_REFUND_ONLY.equals(type);
        boolean allowReturn = TYPE_RETURN_REFUND.equals(type) || TYPE_EXCHANGE.equals(type);
        if (status != null && status == 1 && !allowRefundOnly) {
            throw new BizException("待发货订单仅支持仅退款");
        }
        if (status != null && (status == 2 || status == 3) && !allowReturn) {
            throw new BizException("该订单状态不支持仅退款，可选择退货退款或换货");
        }
        if (status == null || (status != 1 && status != 2 && status != 3)) {
            throw new BizException("当前订单状态不支持申请售后");
        }
        Long count = afterSaleMapper.selectCount(
                new LambdaQueryWrapper<AfterSale>()
                        .eq(AfterSale::getOrderId, orderId)
                        .eq(AfterSale::getStatus, STATUS_PENDING));
        if (count != null && count > 0) {
            throw new BizException("该订单已有进行中的售后申请");
        }
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long merchantId, Long afterSaleId) {
        AfterSale afterSale = getAndCheckScope(merchantId, afterSaleId);
        OrderInfo order = orderService.getById(afterSale.getOrderId());
        if (order == null) {
            throw new BizException("关联订单不存在");
        }
        boolean refundType = TYPE_REFUND_ONLY.equals(afterSale.getType())
                || TYPE_RETURN_REFUND.equals(afterSale.getType());
        // 幂等：原子抢占（条件更新 PENDING→APPROVED），并发/重复提交只有一条能抢到，
        // 抢不到的直接拒绝；退款任一步失败事务整体回滚，抢占状态一并还原，可重试
        int rows = afterSaleMapper.update(null, new LambdaUpdateWrapper<AfterSale>()
                .eq(AfterSale::getId, afterSaleId)
                .eq(AfterSale::getStatus, STATUS_PENDING)
                .set(AfterSale::getStatus, STATUS_APPROVED)
                .set(AfterSale::getHandleTime, LocalDateTime.now()));
        if (rows == 0) {
            throw new BizException("该售后单已处理，请勿重复操作");
        }
        if (refundType) {
            if (order.getRefundStatus() != null && order.getRefundStatus() == 2) {
                throw new BizException("该订单已退款，请勿重复操作");
            }
            BigDecimal amount = afterSale.getRefundAmount();
            // 1. 退款给用户（mock 本地成功；微信调 V3 退款；失败抛异常整体回滚）
            paymentService.refund(order.getId(), amount);
            // 2. 订单标记已退款
            orderService.update(null, new LambdaUpdateWrapper<OrderInfo>()
                    .eq(OrderInfo::getId, order.getId())
                    .set(OrderInfo::getRefundStatus, 2));
            // 3. 商家退款扣回流水
            fundService.refundDeduct(merchantId, order.getId(), amount);
            // 4. 记录退款完成时间
            afterSaleMapper.update(null, new LambdaUpdateWrapper<AfterSale>()
                    .eq(AfterSale::getId, afterSaleId)
                    .set(AfterSale::getRefundTime, LocalDateTime.now()));
        }
        log.info("售后单同意 afterSaleId={} type={} refund={}", afterSaleId, afterSale.getType(), refundType);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long merchantId, Long afterSaleId, String rejectReason) {
        AfterSale afterSale = getAndCheckScope(merchantId, afterSaleId);
        if (rejectReason == null || rejectReason.isBlank()) {
            throw new BizException("请填写拒绝原因");
        }
        // 幂等：原子抢占（条件更新 PENDING→REJECTED），并发/重复提交只有一条生效
        int rows = afterSaleMapper.update(null, new LambdaUpdateWrapper<AfterSale>()
                .eq(AfterSale::getId, afterSaleId)
                .eq(AfterSale::getStatus, STATUS_PENDING)
                .set(AfterSale::getStatus, STATUS_REJECTED)
                .set(AfterSale::getRejectReason, rejectReason)
                .set(AfterSale::getHandleTime, LocalDateTime.now()));
        if (rows == 0) {
            throw new BizException("该售后单已处理，请勿重复操作");
        }
        log.info("售后单拒绝 afterSaleId={} merchantId={}", afterSaleId, merchantId);
    }

    @Override
    public Page<com.xs.sheepaimall.vo.AfterSaleVO> pageByUser(Long userId, int pageNum, int pageSize) {
        Page<AfterSale> page = afterSaleMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<AfterSale>()
                        .eq(AfterSale::getUserId, userId)
                        .orderByDesc(AfterSale::getId));
        return toVoPage(page, pageNum, pageSize);
    }

    @Override
    public Page<com.xs.sheepaimall.vo.AfterSaleVO> pageByMerchant(Long merchantId, int pageNum, int pageSize, String status) {
        LambdaQueryWrapper<AfterSale> wrapper = new LambdaQueryWrapper<AfterSale>()
                .eq(AfterSale::getMerchantId, merchantId)
                .eq(status != null && !status.isBlank(), AfterSale::getStatus, status)
                .orderByDesc(AfterSale::getId);
        Page<AfterSale> page = afterSaleMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return toVoPage(page, pageNum, pageSize);
    }

    /** 售后单分页 → VO 分页：批量补充订单商品明细（SPU 名称） */
    private Page<com.xs.sheepaimall.vo.AfterSaleVO> toVoPage(Page<AfterSale> page, int pageNum, int pageSize) {
        Page<com.xs.sheepaimall.vo.AfterSaleVO> voPage = new Page<>(pageNum, pageSize);
        voPage.setTotal(page.getTotal());
        List<AfterSale> records = page.getRecords();
        if (records.isEmpty()) {
            voPage.setRecords(java.util.Collections.emptyList());
            return voPage;
        }
        // 批量查订单明细 + SPU 名称
        List<Long> orderIds = records.stream().map(AfterSale::getOrderId).distinct().toList();
        Map<Long, List<OrderItem>> itemMap = orderItemService.list(
                        new LambdaQueryWrapper<OrderItem>().in(OrderItem::getOrderId, orderIds))
                .stream().collect(Collectors.groupingBy(OrderItem::getOrderId));
        Set<Long> spuIds = itemMap.values().stream().flatMap(List::stream)
                .map(OrderItem::getSpuId).collect(Collectors.toSet());
        Map<Long, String> spuNameMap = spuIds.isEmpty() ? Map.of() : spuService.listByIds(spuIds)
                .stream().collect(Collectors.toMap(Spu::getId, Spu::getName, (a, b) -> a));

        List<com.xs.sheepaimall.vo.AfterSaleVO> voList = records.stream().map(a -> {
            com.xs.sheepaimall.vo.AfterSaleVO vo = new com.xs.sheepaimall.vo.AfterSaleVO();
            BeanUtil.copyProperties(a, vo);
            List<com.xs.sheepaimall.vo.OrderItemVO> items = itemMap.getOrDefault(a.getOrderId(), List.of())
                    .stream().map(it -> {
                        com.xs.sheepaimall.vo.OrderItemVO iv = new com.xs.sheepaimall.vo.OrderItemVO();
                        BeanUtil.copyProperties(it, iv);
                        iv.setSpuName(spuNameMap.get(it.getSpuId()));
                        return iv;
                    }).collect(Collectors.toList());
            vo.setItems(items);
            return vo;
        }).collect(Collectors.toList());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public Map<Long, String> pendingStatusByOrderIds(Collection<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return afterSaleMapper.selectList(
                        new LambdaQueryWrapper<AfterSale>()
                                .in(AfterSale::getOrderId, orderIds)
                                .eq(AfterSale::getStatus, STATUS_PENDING))
                .stream().collect(Collectors.toMap(AfterSale::getOrderId, AfterSale::getStatus, (a, b) -> a));
    }

    /** 查询并校验售后单归属当前商家 */
    private AfterSale getAndCheckScope(Long merchantId, Long afterSaleId) {
        AfterSale afterSale = afterSaleMapper.selectById(afterSaleId);
        if (afterSale == null) {
            throw new BizException("售后单不存在");
        }
        if (!afterSale.getMerchantId().equals(merchantId)) {
            throw new BizException("无权操作此售后单");
        }
        return afterSale;
    }

    /** 从订单项反查商家（一单必须单一商家，与分佣结算口径一致） */
    private Long resolveMerchantId(Long orderId) {
        List<OrderItem> items = orderItemService.lambdaQuery()
                .eq(OrderItem::getOrderId, orderId)
                .list();
        if (items.isEmpty()) {
            return null;
        }
        Set<Long> spuIds = items.stream().map(OrderItem::getSpuId).collect(Collectors.toSet());
        Set<Long> merchantIds = new HashSet<>();
        for (Spu spu : spuService.listByIds(spuIds)) {
            merchantIds.add(spu.getMerchantId());
        }
        merchantIds.remove(null);
        return merchantIds.size() == 1 ? merchantIds.iterator().next() : null;
    }
}
