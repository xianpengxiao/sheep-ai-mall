package com.xs.sheepaimall.scheduler;

import com.xs.sheepaimall.service.MerchantDsrService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * DSR 每日滚动计算定时任务 —— 每天凌晨 2:00 重新计算所有商家的近90天DSR评分。
 */
@Component
public class DsrDailyScheduler {

    private static final Logger log = LoggerFactory.getLogger(DsrDailyScheduler.class);

    @Resource
    private MerchantDsrService merchantDsrService;

    /** 每天凌晨 2:00 执行 */
    @Scheduled(cron = "0 0 2 * * ?")
    public void dailyCalculate() {
        log.info("开始每日DSR滚动计算...");
        long start = System.currentTimeMillis();
        merchantDsrService.dailyCalculateAll();
        long cost = System.currentTimeMillis() - start;
        log.info("每日DSR滚动计算完成，耗时 {}ms", cost);
    }
}
