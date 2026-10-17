package com.xs.sheepaimall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xs.sheepaimall.entity.CsDailyUsage;
import com.xs.sheepaimall.entity.CsUsageRecord;
import com.xs.sheepaimall.mapper.CsDailyUsageMapper;
import com.xs.sheepaimall.mapper.CsUsageRecordMapper;
import com.xs.sheepaimall.service.agent.ChatMeter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

/**
 * Token 计量落账 —— 异步双写（设计 D12）：
 * <ul>
 *   <li>cs_usage_record：逐请求插入（input/output/total）</li>
 *   <li>cs_daily_usage：用户行(user_id+stat_date)与商家行(merchant_id+stat_date) upsert 日聚合</li>
 *   <li>Redis 累计键：cs:quota:u:{userId}:{yyyyMMdd} / cs:quota:m:{merchantId}:{yyyyMMdd} hash{req,tokens}，
 *       cs:quota:mm:{merchantId}:{yyyyMM} int——用实际 Usage 写入（本阶段为检查-only模式，无双写冲突）</li>
 * </ul>
 * <p>全部失败仅告警。Redis 累计为实时额度依据，MySQL 为异步对账持久化。</p>
 */
@Slf4j
@Service
public class MeteringService {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

    /** 日累计键 TTL 25h，覆盖跨时区/跨天窗口 */
    private static final Duration DAILY_TTL = Duration.ofHours(25);
    /** 月累计键 TTL 35 天，覆盖完整自然月 */
    private static final Duration MONTH_TTL = Duration.ofDays(35);

    private static final String KEY_USER_DAILY = "cs:quota:u:";
    private static final String KEY_MERCHANT_DAILY = "cs:quota:m:";
    private static final String KEY_MERCHANT_MONTH = "cs:quota:mm:";

    @Autowired
    private CsUsageRecordMapper csUsageRecordMapper;

    @Autowired
    private CsDailyUsageMapper csDailyUsageMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** 异步落账：逐请求记录 + 日聚合 upsert + Redis 累计，失败仅告警 */
    public void record(Long userId, Long merchantId, Long sessionId, ChatMeter meter) {
        CompletableFuture.runAsync(() -> doRecord(userId, merchantId, sessionId, meter));
    }

    private void doRecord(Long userId, Long merchantId, Long sessionId, ChatMeter meter) {
        int total = meter.getTotalTokens();
        LocalDate today = LocalDate.now();
        String day = today.format(DAY_FORMAT);

        // 1. 逐请求计量记录
        try {
            CsUsageRecord record = new CsUsageRecord();
            record.setUserId(userId);
            record.setMerchantId(merchantId);
            record.setSessionId(sessionId);
            record.setInputTokens(meter.getInputTokens());
            record.setOutputTokens(meter.getOutputTokens());
            record.setTotalTokens(total);
            csUsageRecordMapper.insert(record);
        } catch (Exception e) {
            log.error("计量记录写入失败 sessionId={}", sessionId, e);
        }

        // 2. 日聚合 upsert：用户行 + 商家行（商家会话才有）
        upsertDaily(userId, null, today, total);
        if (merchantId != null) {
            upsertDaily(null, merchantId, today, total);
        }

        // 3. Redis 实时累计（额度判定的实时依据）
        try {
            String userKey = KEY_USER_DAILY + userId + ":" + day;
            stringRedisTemplate.opsForHash().increment(userKey, "req", 1);
            stringRedisTemplate.opsForHash().increment(userKey, "tokens", total);
            stringRedisTemplate.expire(userKey, DAILY_TTL);

            if (merchantId != null) {
                String merchantKey = KEY_MERCHANT_DAILY + merchantId + ":" + day;
                stringRedisTemplate.opsForHash().increment(merchantKey, "req", 1);
                stringRedisTemplate.opsForHash().increment(merchantKey, "tokens", total);
                stringRedisTemplate.expire(merchantKey, DAILY_TTL);

                String monthKey = KEY_MERCHANT_MONTH + merchantId + ":" + today.format(MONTH_FORMAT);
                stringRedisTemplate.opsForValue().increment(monthKey, total);
                stringRedisTemplate.expire(monthKey, MONTH_TTL);
            }
        } catch (Exception e) {
            log.warn("计量累计写入 Redis 失败 sessionId={}: {}", sessionId, e.getMessage());
        }
    }

    /** 日聚合 upsert：按 (userId,statDate) 或 (merchantId,statDate) 唯一行累加，无则插入 */
    private void upsertDaily(Long userId, Long merchantId, LocalDate statDate, int total) {
        try {
            LambdaQueryWrapper<CsDailyUsage> wrapper = new LambdaQueryWrapper<CsDailyUsage>()
                    .eq(userId != null, CsDailyUsage::getUserId, userId)
                    .eq(merchantId != null, CsDailyUsage::getMerchantId, merchantId)
                    .eq(CsDailyUsage::getStatDate, statDate);
            CsDailyUsage usage = csDailyUsageMapper.selectOne(wrapper);
            if (usage == null) {
                usage = new CsDailyUsage();
                usage.setUserId(userId);
                usage.setMerchantId(merchantId);
                usage.setStatDate(statDate);
                usage.setRequestCount(1);
                usage.setTokenUsed((long) total);
                csDailyUsageMapper.insert(usage);
            } else {
                csDailyUsageMapper.update(null, new LambdaUpdateWrapper<CsDailyUsage>()
                        .eq(CsDailyUsage::getId, usage.getId())
                        .setSql("request_count = request_count + 1")
                        .setSql("token_used = token_used + " + total));
            }
        } catch (Exception e) {
            log.warn("日用量聚合写入失败 userId={}, merchantId={}: {}", userId, merchantId, e.getMessage());
        }
    }
}
