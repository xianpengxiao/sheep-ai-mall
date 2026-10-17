package com.xs.sheepaimall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xs.sheepaimall.entity.CsAgentConfig;
import com.xs.sheepaimall.entity.CsPlan;
import com.xs.sheepaimall.mapper.CsPlanMapper;
import com.xs.sheepaimall.mapper.MerchantAiServiceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 客服额度体系（设计 D9/D10）—— 用户日限 → 商家日限 → 商家月余额 三级检查。
 * <p>本阶段实现为"仅检查不预扣"：累计键由 MeteringService 在实际 Usage 返回后写入实际值，
 * 避免预扣估算值与实际值的差额回填复杂度。Redis 重启丢当日累计的代价是额度多放行，可接受。</p>
 * <p>Redis 异常 → 放行 + warn（护栏失效不造成客服不可用）。额度总开关见 cs_agent_config.quota_enabled。</p>
 */
@Slf4j
@Service
public class QuotaService {

    /** 额度检查结果 */
    public enum QuotaCheckResult {
        /** 通过 */
        PASS,
        /** 商家月度额度已耗尽 */
        MERCHANT_MONTH_EXHAUSTED,
        /** 商家月度余额不足以覆盖本次预估消耗 */
        NOT_ENOUGH_MERCHANT_BALANCE
    }

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

    private static final String KEY_MERCHANT_MONTH = "cs:quota:mm:";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private CsAgentConfigService csAgentConfigService;

    @Autowired
    private MerchantAiServiceMapper merchantAiServiceMapper;

    @Autowired
    private CsPlanMapper csPlanMapper;

    /**
     * 三级额度检查（不写累计，实际消耗由 MeteringService 落账）
     *
     * @param userId          用户ID
     * @param merchantId      商家ID（null=平台会话，只查用户级）
     * @param estimatedTokens 本次请求预估Token（字符/4估算，用于日/月预算判断）
     */
    public QuotaCheckResult checkAndConsume(Long userId, Long merchantId, int estimatedTokens) {
        CsAgentConfig config = csAgentConfigService.getConfig();
        if (config.getQuotaEnabled() == null || config.getQuotaEnabled() != 1) {
            return QuotaCheckResult.PASS;
        }
        try {
            // ① 用户级额度：按产品决策不限制用户咨询次数/Token，仅保留分钟级限流防刷（RateLimitService）。
            // ② 商家日限（日请求数/日Token）：按产品决策不做日维度限制，仅保留 ③ 月度总量限制。
            // 平台会话（merchantId=null）无商家额度约束，直接放行。
            if (merchantId == null) {
                return QuotaCheckResult.PASS;
            }

            // ③ 商家月度总量：月度额度快照（null 回退套餐值）− 当月已用
            com.xs.sheepaimall.entity.MerchantAiService service = merchantAiServiceMapper.selectOne(
                    new LambdaQueryWrapper<com.xs.sheepaimall.entity.MerchantAiService>()
                            .eq(com.xs.sheepaimall.entity.MerchantAiService::getMerchantId, merchantId));
            CsPlan plan = service == null || service.getMonthlyTokenQuota() == null
                    ? loadPlan(service) : null;
            long monthlyQuota = service != null && service.getMonthlyTokenQuota() != null
                    ? service.getMonthlyTokenQuota()
                    : (plan != null ? plan.getMonthlyTokenQuota() : Long.MAX_VALUE);
            String monthUsedStr = stringRedisTemplate.opsForValue()
                    .get(KEY_MERCHANT_MONTH + merchantId + ":" + LocalDate.now().format(MONTH_FORMAT));
            long monthUsed = toLong(monthUsedStr);
            if (monthUsed >= monthlyQuota) {
                return QuotaCheckResult.MERCHANT_MONTH_EXHAUSTED;
            }
            if (monthlyQuota - monthUsed < estimatedTokens) {
                return QuotaCheckResult.NOT_ENOUGH_MERCHANT_BALANCE;
            }
            return QuotaCheckResult.PASS;
        } catch (Exception e) {
            // Redis 异常放行 + 告警：可用性优先
            log.warn("额度检查异常，放行 userId={}, merchantId={}: {}", userId, merchantId, e.getMessage());
            return QuotaCheckResult.PASS;
        }
    }

    /** 回退查套餐：优先按 plan_id，其次启用的第一档套餐 */
    private CsPlan loadPlan(com.xs.sheepaimall.entity.MerchantAiService service) {
        try {
            if (service != null && service.getPlanId() != null) {
                CsPlan plan = csPlanMapper.selectById(service.getPlanId());
                if (plan != null) {
                    return plan;
                }
            }
            return csPlanMapper.selectOne(new LambdaQueryWrapper<CsPlan>()
                    .eq(CsPlan::getStatus, 1)
                    .last("LIMIT 1"));
        } catch (Exception e) {
            log.warn("套餐回退查询失败: {}", e.getMessage());
            return null;
        }
    }

    private long toLong(Object value) {
        if (value == null) return 0L;
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
