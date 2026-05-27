package com.xs.sheepaimall.common;

import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 缓存工具：防穿透（缓存空值）、防击穿（分布式锁）、防雪崩（TTL 随机抖动）
 */
@Component
public class CacheHelper {

    private static final Logger log = LoggerFactory.getLogger(CacheHelper.class);

    /**
     * 空值标记，用于防穿透
     */
    public static final String NULL_MARKER = "__NULL__";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // ==================== 基础操作 ====================

    /** 读缓存 */
    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    /** 写缓存（带抖动 TTL） */
    public void set(String key, String value, long ttlSeconds) {
        stringRedisTemplate.opsForValue().set(key, value, jitterTtl(ttlSeconds), TimeUnit.SECONDS);
    }

    /** 删缓存 */
    public void evict(String key) {
        stringRedisTemplate.delete(key);
    }

    // ==================== 防穿透：缓存空值 ====================

    /**
     * 写到缓存，值为 Null 时写入 NULL_MARKER 防穿透
     */
    public void setWithNullGuard(String key, String value, long ttlSeconds) {
        if (value == null) {
            stringRedisTemplate.opsForValue().set(key, NULL_MARKER,
                    jitterTtl(CacheConstants.NULL_VALUE_TTL),
                    TimeUnit.SECONDS);
        } else {
            set(key, value, ttlSeconds);
        }
    }

    /**
     * 读缓存，NULL_MARKER 视为 null（代表缓存命中但数据为空）
     */
    public String getWithNullGuard(String key) {
        String value = get(key);
        if (NULL_MARKER.equals(value)) {
            return null; // 命中空值缓存，不算穿透
        }
        return value;
    }

    // ==================== 防击穿：分布式锁 + 缓存重建 ====================

    /**
     * 带防击穿保护的"读或加载"操作。
     * 缓存命中直接返回；未命中时尝试获取分布式锁，
     * 获取成功则调用 dbFetcher 加载数据并回写缓存，失败则等待后重试。
     *
     * @param key       缓存 key
     * @param dbFetcher 数据库加载函数
     * @param ttlSeconds 缓存过期秒数
     * @return 缓存值（json），可能为 null（防穿透标记）
     */
    public String getOrFetch(String key, Supplier<String> dbFetcher, long ttlSeconds) {
        // 1. 先读缓存
        String cached = getWithNullGuard(key);
        if (cached != null) {
            return cached;
        }
        // 2. 检查是否命中空值标记
        if (NULL_MARKER.equals(get(key))) {
            return null;
        }
        // 3. 未命中，尝试获取分布式锁重建缓存
        String lockKey = CacheConstants.LOCK_PREFIX + "::" + key;
        String lockVal = UUID.randomUUID().toString();
        boolean locked = tryLock(lockKey, lockVal);
        try {
            if (locked) {
                // 获取锁成功，双重检查
                cached = getWithNullGuard(key);
                if (cached != null) {
                    return cached;
                }
                if (NULL_MARKER.equals(get(key))) {
                    return null;
                }
                // 查数据库
                String value = dbFetcher.get();
                setWithNullGuard(key, value, ttlSeconds);
                return value;
            } else {
                // 获取锁失败，等待后重试读缓存
                long start = System.currentTimeMillis();
                while (System.currentTimeMillis() - start < CacheConstants.LOCK_WAIT_MS) {
                    TimeUnit.MILLISECONDS.sleep(CacheConstants.LOCK_RETRY_INTERVAL_MS);
                    cached = getWithNullGuard(key);
                    if (cached != null) {
                        return cached;
                    }
                    if (NULL_MARKER.equals(get(key))) {
                        return null;
                    }
                }
                // 等待超时，直接查库但不回写（防止雪上加霜）
                log.warn("缓存重建等待超时 key={}, 降级直查", key);
                return dbFetcher.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return dbFetcher.get();
        } finally {
            if (locked) {
                releaseLock(lockKey, lockVal);
            }
        }
    }

    // ==================== 批量清除 ====================

    /** 按前缀清除缓存 */
    public void evictByPrefix(String prefix) {
        var keys = stringRedisTemplate.keys(prefix + "*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    /** 清除分类树缓存 */
    public void evictCategoryTree() {
        evict(CacheConstants.CATEGORY_TREE);
        log.debug("分类树缓存已清除");
    }

    /** 清除商品详情缓存 */
    public void evictSpuDetail(Long spuId) {
        evict(CacheConstants.SPU_DETAIL + "::" + spuId);
        log.debug("商品详情缓存已清除 spuId={}", spuId);
    }

    /** 清除热门商品分页缓存 */
    public void evictSpuHotPage() {
        evictByPrefix(CacheConstants.SPU_HOT_PAGE + "::");
        log.debug("热门商品分页缓存已清除");
    }

    // ==================== 内部工具 ====================

    /**
     * 计算带随机抖动的 TTL，防雪崩。
     * 抖动范围：baseTtl * (1 ± jitterRatio)，不低于 60s。
     */
    private long jitterTtl(long baseTtl) {
        double ratio = CacheConstants.TTL_JITTER_RATIO;
        double jitter = ThreadLocalRandom.current().nextDouble(-ratio, ratio);
        long ttl = (long) (baseTtl * (1 + jitter));
        return Math.max(ttl, 60);
    }

    /** 尝试获取分布式锁（SETNX + UUID + 过期时间） */
    private boolean tryLock(String lockKey, String lockVal) {
        Boolean ok = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockVal, CacheConstants.LOCK_LEASE_MS, TimeUnit.MILLISECONDS);
        return Boolean.TRUE.equals(ok);
    }

    /**
     * 安全释放分布式锁 —— Lua 脚本校验 value 匹配后再删除，防止误删他人的锁
     */
    private void releaseLock(String lockKey, String lockVal) {
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                        "return redis.call('del', KEYS[1]) " +
                        "else return 0 end";
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(script, Long.class);
        stringRedisTemplate.execute(redisScript, Collections.singletonList(lockKey), lockVal);
    }
}
