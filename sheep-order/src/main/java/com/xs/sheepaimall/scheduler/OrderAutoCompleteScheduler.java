package com.xs.sheepaimall.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xs.sheepaimall.entity.OrderInfo;
import com.xs.sheepaimall.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 自动确认收货定时任务 —— 每天凌晨扫描已发货超过7天的订单，自动完成并生成待评记录。
 */
@Component
public class OrderAutoCompleteScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderAutoCompleteScheduler.class);

    private static final int BATCH_SIZE = 100;

    /** 发货后自动确认收货天数 */
    private static final int AUTO_COMPLETE_DAYS = 7;

    @Autowired
    private OrderService orderService;

    /** 每天凌晨 2:30 执行 */
    @Scheduled(cron = "0 30 2 * * ?")
    public void autoCompleteOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusDays(AUTO_COMPLETE_DAYS);
        log.info("开始自动确认收货（发货截止时间: {}）", deadline);

        List<OrderInfo> pendingOrders = orderService.list(
                new LambdaQueryWrapper<OrderInfo>()
                        .eq(OrderInfo::getStatus, 2)
                        .lt(OrderInfo::getDeliveryTime, deadline)
                        .last("LIMIT " + BATCH_SIZE));

        if (pendingOrders.isEmpty()) {
            return;
        }

        int success = 0;
        int fail = 0;
        for (OrderInfo order : pendingOrders) {
            try {
                orderService.confirmReceipt(order.getId());
                success++;
            } catch (Exception e) {
                fail++;
                log.error("自动确认收货失败 orderId={}", order.getId(), e);
            }
        }

        log.info("自动确认收货完成 success={} fail={}", success, fail);
    }
}
