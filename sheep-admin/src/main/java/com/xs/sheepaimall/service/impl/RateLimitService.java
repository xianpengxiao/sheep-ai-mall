package com.xs.sheepaimall.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 客服请求限流 —— Redis 固定窗口（INCR + 首次 EXPIRE 60s）。
 * <p>Redis 异常时放行并告警（护栏失效不造成客服不可用，对齐既有降级哲学）。</p>
 */
@Slf4j
@Service
public class RateLimitService {

    private static final Duration WINDOW = Duration.ofSeconds(60);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 尝试获取一个请求配额
     *
     * @param key   限流键（cs:rl:u:{userId} / cs:rl:m:{merchantId}）
     * @param limit 窗口内允许的最大次数
     * @return true=放行 false=超限
     */
    public boolean tryAcquire(String key, int limit) {
        try {
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count == null) {
                return true;
            }
            if (count == 1L) {
                stringRedisTemplate.expire(key, WINDOW);
            }
            return count <= limit;
        } catch (Exception e) {
            // Redis 异常放行 + 告警：可用性优先
            log.warn("限流检查异常，放行 key={}: {}", key, e.getMessage());
            return true;
        }
    }
}
