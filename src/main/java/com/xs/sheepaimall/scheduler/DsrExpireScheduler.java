package com.xs.sheepaimall.scheduler;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xs.sheepaimall.entity.ProductReview;
import com.xs.sheepaimall.mapper.ProductReviewMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 评价过期定时任务 —— 每小时扫描过期未评的记录，标记为已过期。
 */
@Component
public class DsrExpireScheduler {

    private static final Logger log = LoggerFactory.getLogger(DsrExpireScheduler.class);

    @Autowired
    private ProductReviewMapper productReviewMapper;

    /** 每小时执行一次 */
    @Scheduled(fixedDelay = 3600_000)
    public void expireReviews() {
        LocalDateTime now = LocalDateTime.now();
        int updated = productReviewMapper.update(null,
                new LambdaUpdateWrapper<ProductReview>()
                        .eq(ProductReview::getReviewStatus, 0)
                        .lt(ProductReview::getExpiredAt, now)
                        .set(ProductReview::getReviewStatus, 2));
        if (updated > 0) {
            log.info("评价过期处理完成，共 {} 条已标记为过期", updated);
        }
    }
}
