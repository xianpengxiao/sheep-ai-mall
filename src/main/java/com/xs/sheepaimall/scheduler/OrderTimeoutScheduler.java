package com.xs.sheepaimall.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xs.sheepaimall.common.RabbitMQConstants;
import com.xs.sheepaimall.entity.OrderInfo;
import com.xs.sheepaimall.entity.OrderItem;
import com.xs.sheepaimall.entity.Sku;
import com.xs.sheepaimall.entity.Spu;
import com.xs.sheepaimall.service.OrderItemService;
import com.xs.sheepaimall.service.OrderService;
import com.xs.sheepaimall.service.SkuService;
import com.xs.sheepaimall.service.SpuService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单超时自动取消定时任务 —— 扫描 status=0 且超过支付时限的订单，自动取消并释放库存。
 *
 * 定时频率：每 30 秒执行一次，每次最多处理 100 笔。
 * 释放库存不走 MQ，直接在定时任务中同步回滚（减少链路开销）。
 */
@Component
public class OrderTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutScheduler.class);

    private static final int BATCH_SIZE = 100;

    @Value("${sheep.mall.order.pay-timeout-minutes:15}")
    private int payTimeoutMinutes;

    @Resource
    private OrderService orderService;

    @Resource
    private OrderItemService orderItemService;

    @Resource
    private SkuService skuService;

    @Resource
    private SpuService spuService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /** 每 30 秒扫描一次超时未支付订单 */
    @Scheduled(fixedDelay = 30_000)
    public void cancelTimeoutOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(payTimeoutMinutes);
        log.debug("开始扫描超时订单（截止时间: {}）", deadline);

        List<OrderInfo> expiredOrders = orderService.list(
                new LambdaQueryWrapper<OrderInfo>()
                        .eq(OrderInfo::getStatus, 0)
                        .lt(OrderInfo::getCreateTime, deadline)
                        .last("LIMIT " + BATCH_SIZE));

        if (expiredOrders.isEmpty()) {
            return;
        }

        log.info("发现 {} 笔超时订单，开始批量取消", expiredOrders.size());
        int successCount = 0;
        int failCount = 0;

        for (OrderInfo order : expiredOrders) {
            try {
                cancelOrderAndReleaseStock(order);
                successCount++;
            } catch (Exception e) {
                failCount++;
                log.error("超时取消订单异常 orderId={} orderNo={}", order.getId(), order.getOrderNo(), e);
            }
        }

        log.info("超时订单处理完成 success={} fail={}", successCount, failCount);
    }

    /** 取消单笔超时订单，同步释放库存 */
    private void cancelOrderAndReleaseStock(OrderInfo order) {
        Long orderId = order.getId();

        // 1. 检查库存是否已被锁定
        String lockStatusKey = RabbitMQConstants.STOCK_LOCKED_STATUS_PREFIX + orderId;
        String lockStatus = stringRedisTemplate.opsForValue().get(lockStatusKey);

        if ("LOCKED".equals(lockStatus)) {
            // 已锁定 → 归还库存 + 回退销量
            List<OrderItem> items = orderItemService.list(
                    new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
            for (OrderItem item : items) {
                skuService.update(new LambdaUpdateWrapper<Sku>()
                        .eq(Sku::getId, item.getSkuId())
                        .setSql("stock = stock + " + item.getQuantity()));
                spuService.update(new LambdaUpdateWrapper<Spu>()
                        .eq(Spu::getId, item.getSpuId())
                        .setSql("sales_count = sales_count - " + item.getQuantity()));
            }
            stringRedisTemplate.delete(lockStatusKey);
            log.info("超时订单释放库存 orderId={}", orderId);
        }

        // 2. 更新订单状态为已取消
        order.setStatus(4);
        order.setCancelTime(LocalDateTime.now());
        orderService.updateById(order);

        log.info("超时订单已取消 orderId={} orderNo={}", orderId, order.getOrderNo());
    }
}
