package com.xs.sheepaimall.scheduler;

import com.xs.sheepaimall.feign.OrderFeignClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 评价过期定时任务 —— 每小时扫描过期未评的记录，标记为已过期。
 */
@Component
public class DsrExpireScheduler {

    private static final Logger log = LoggerFactory.getLogger(DsrExpireScheduler.class);

    @Autowired
    private OrderFeignClient orderFeignClient;

    /** 每小时执行一次 */
    @Scheduled(fixedDelay = 3600_000)
    public void expireReviews() {
        int updated = orderFeignClient.expireReviews();
        if (updated > 0) {
            log.info("评价过期处理完成，共 {} 条已标记为过期", updated);
        }
    }
}
