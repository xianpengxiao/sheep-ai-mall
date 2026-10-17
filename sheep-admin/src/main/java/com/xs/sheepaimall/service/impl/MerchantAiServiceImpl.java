package com.xs.sheepaimall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xs.sheepaimall.common.BizException;
import com.xs.sheepaimall.dto.MerchantPaymentNotifyDTO;
import com.xs.sheepaimall.entity.CsPlan;
import com.xs.sheepaimall.entity.Merchant;
import com.xs.sheepaimall.entity.MerchantPaymentRecord;
import com.xs.sheepaimall.mapper.CsPlanMapper;
import com.xs.sheepaimall.mapper.MerchantAiServiceMapper;
import com.xs.sheepaimall.mapper.MerchantMapper;
import com.xs.sheepaimall.mapper.MerchantPaymentRecordMapper;
import com.xs.sheepaimall.security.UserContext;
import com.xs.sheepaimall.service.KnowledgeBaseService;
import com.xs.sheepaimall.vo.MerchantAiServiceStatusVO;
import com.xs.sheepaimall.vo.MerchantCsQuotaVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * 商家智能客服服务实现 — 假支付直接成功，开通/续费一体化。
 * <p>注意：实体 {@link com.xs.sheepaimall.entity.MerchantAiService} 与本接口同名，
 * 本类中 import 的是实体，接口以全限定名出现在 implements 子句。</p>
 */
@Slf4j
@Service
public class MerchantAiServiceImpl implements com.xs.sheepaimall.service.MerchantAiService {

    /** 智能客服年费（元），当前为固定定价 */
    private static final BigDecimal AI_SERVICE_YEAR_PRICE = new BigDecimal("299.00");

    /** 业务类型：智能客服开通 */
    private static final String BIZ_TYPE_AI_SERVICE = "AI_SERVICE";

    /** 支付渠道：假支付，接入真实支付后替换 */
    private static final String CHANNEL_MOCK = "MOCK";

    /** 支付状态：成功 */
    private static final int PAY_STATUS_SUCCESS = 1;

    /** 实时累计键前缀，与 MeteringService/QuotaService 保持一致 */
    private static final String KEY_MERCHANT_DAILY = "cs:quota:m:";
    private static final String KEY_MERCHANT_MONTH = "cs:quota:mm:";

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

    @Autowired
    private MerchantMapper merchantMapper;

    @Autowired
    private MerchantPaymentRecordMapper merchantPaymentRecordMapper;

    @Autowired
    private MerchantAiServiceMapper merchantAiServiceMapper;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private CsPlanMapper csPlanMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private CsAgentConfigService csAgentConfigService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MerchantAiServiceStatusVO subscribe() {
        Merchant merchant = getCurrentMerchant();
        Long merchantId = merchant.getId();
        LocalDateTime now = LocalDateTime.now();

        // 假支付：直接生成一条成功流水（结构与真实支付一致，便于后续无缝切换）
        MerchantPaymentRecord record = new MerchantPaymentRecord();
        record.setMerchantId(merchantId);
        record.setBizType(BIZ_TYPE_AI_SERVICE);
        record.setAmount(AI_SERVICE_YEAR_PRICE);
        record.setChannel(CHANNEL_MOCK);
        record.setStatus(PAY_STATUS_SUCCESS);
        record.setOutTradeNo(UUID.randomUUID().toString().replace("-", ""));
        record.setPayTime(now);
        merchantPaymentRecordMapper.insert(record);

        // 开通或续费：renew 已并入 subscribe（未开通则新建，已开通则有效期顺延一年）
        com.xs.sheepaimall.entity.MerchantAiService service = merchantAiServiceMapper.selectOne(
                new LambdaQueryWrapper<com.xs.sheepaimall.entity.MerchantAiService>()
                        .eq(com.xs.sheepaimall.entity.MerchantAiService::getMerchantId, merchantId));
        if (service == null) {
            service = new com.xs.sheepaimall.entity.MerchantAiService();
            service.setMerchantId(merchantId);
            service.setStatus(1);
            service.setStartTime(now);
            service.setExpireTime(now.plusYears(1));
            merchantAiServiceMapper.insert(service);
        } else {
            // 续费：原记录仍在有效期内则自原有效期顺延，否则自当前时间起算一年
            LocalDateTime base = (service.getStatus() != null && service.getStatus() == 1
                    && service.getExpireTime() != null && service.getExpireTime().isAfter(now))
                    ? service.getExpireTime() : now;
            service.setStatus(1);
            if (service.getStartTime() == null) {
                service.setStartTime(now);
            }
            service.setExpireTime(base.plusYears(1));
            merchantAiServiceMapper.updateById(service);
        }

        // 初始化商家专属向量表（幂等：建表语句带 IF NOT EXISTS）
        knowledgeBaseService.createMerchantVectorTable(merchantId);

        log.info("商家智能客服开通/续费成功 merchantId={}, expireTime={}", merchantId, service.getExpireTime());
        return toStatusVO(service);
    }

    @Override
    public MerchantAiServiceStatusVO getStatus() {
        Merchant merchant = getCurrentMerchant();
        com.xs.sheepaimall.entity.MerchantAiService service = merchantAiServiceMapper.selectOne(
                new LambdaQueryWrapper<com.xs.sheepaimall.entity.MerchantAiService>()
                        .eq(com.xs.sheepaimall.entity.MerchantAiService::getMerchantId, merchant.getId()));
        // 总闸状态随状态接口下发：总闸关闭时商家端开关应置灰（状态保留、解禁恢复）
        Integer globalAiEnabled = csAgentConfigService.getConfig().getAiEnabled();
        if (service == null) {
            return MerchantAiServiceStatusVO.builder()
                    .subscribed(false).status(0).aiEnabled(0).globalAiEnabled(globalAiEnabled).build();
        }
        MerchantAiServiceStatusVO vo = toStatusVO(service);
        vo.setGlobalAiEnabled(globalAiEnabled);
        return vo;
    }

    @Override
    public void handlePaymentNotify(MerchantPaymentNotifyDTO dto) {
        // TODO 接入真实支付后：在此完成验签、幂等校验，并按 outTradeNo 更新 merchant_payment_record 流水状态，
        //  支付成功后再触发开通/续费逻辑（当前假支付直接成功，无需回调处理）。
        log.info("收到商家服务支付回调（当前为假支付桩，未做处理）: outTradeNo={}, tradeStatus={}",
                dto == null ? null : dto.getOutTradeNo(), dto == null ? null : dto.getTradeStatus());
    }

    @Override
    public MerchantCsQuotaVO getQuota() {
        Merchant merchant = getCurrentMerchant();
        Long merchantId = merchant.getId();
        com.xs.sheepaimall.entity.MerchantAiService service = merchantAiServiceMapper.selectOne(
                new LambdaQueryWrapper<com.xs.sheepaimall.entity.MerchantAiService>()
                        .eq(com.xs.sheepaimall.entity.MerchantAiService::getMerchantId, merchantId));

        // 未开通：返回空视图（额度全部为空/0）
        if (service == null) {
            return MerchantCsQuotaVO.builder()
                    .todayRequestCount(0).todayTokenUsed(0L)
                    .monthlyTokenUsed(0L)
                    .build();
        }

        // 额度配置：merchant_ai_service 自定义值优先，null 回退套餐默认（与 QuotaService 同一约束链口径）
        CsPlan plan = loadPlan(service);
        Long monthlyQuota = service.getMonthlyTokenQuota() != null
                ? service.getMonthlyTokenQuota()
                : (plan != null ? plan.getMonthlyTokenQuota() : null);
        Integer dailyReqLimit = service.getDailyRequestLimit() != null
                ? service.getDailyRequestLimit()
                : (plan != null ? plan.getDailyRequestLimit() : null);
        Long dailyTokenLimit = service.getDailyTokenLimit() != null
                ? Long.valueOf(service.getDailyTokenLimit())
                : (plan != null && plan.getDailyTokenLimit() != null
                        ? Long.valueOf(plan.getDailyTokenLimit()) : null);

        // Redis 实时累计（MeteringService 写入）：月已用 int + 今日 hash{req,tokens}，异常降级 0 并 warn
        LocalDate today = LocalDate.now();
        long monthUsed = 0L;
        long todayReq = 0L;
        long todayTokens = 0L;
        try {
            String monthUsedStr = stringRedisTemplate.opsForValue()
                    .get(KEY_MERCHANT_MONTH + merchantId + ":" + today.format(MONTH_FORMAT));
            monthUsed = parseLong(monthUsedStr);
        } catch (Exception e) {
            log.warn("商家月额度累计读取失败 merchantId={}: {}", merchantId, e.getMessage());
        }
        try {
            Map<Object, Object> daily = stringRedisTemplate.opsForHash()
                    .entries(KEY_MERCHANT_DAILY + merchantId + ":" + today.format(DAY_FORMAT));
            todayReq = parseLong(daily.get("req"));
            todayTokens = parseLong(daily.get("tokens"));
        } catch (Exception e) {
            log.warn("商家日用量累计读取失败 merchantId={}: {}", merchantId, e.getMessage());
        }

        Long remain = monthlyQuota == null ? null : Math.max(0L, monthlyQuota - monthUsed);
        return MerchantCsQuotaVO.builder()
                .planName(plan != null ? plan.getName() : null)
                .monthlyTokenQuota(monthlyQuota)
                .monthlyTokenUsed(monthUsed)
                .monthlyTokenRemain(remain)
                .todayRequestCount((int) todayReq)
                .todayTokenUsed(todayTokens)
                .dailyRequestLimit(dailyReqLimit)
                .dailyTokenLimit(dailyTokenLimit)
                .build();
    }

    // ======================== 辅助方法 ========================

    /** 回退查套餐：优先按 plan_id，其次启用的第一档套餐（与 QuotaService.loadPlan 同口径） */
    private CsPlan loadPlan(com.xs.sheepaimall.entity.MerchantAiService service) {
        try {
            if (service.getPlanId() != null) {
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

    /** 安全解析累计值：null/非数字一律按 0 */
    private long parseLong(Object value) {
        if (value == null) return 0L;
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }


    /** 当前登录用户对应的商家，不存在则抛业务异常 */
    private Merchant getCurrentMerchant() {
        Merchant merchant = merchantMapper.selectOne(
                new LambdaQueryWrapper<Merchant>().eq(Merchant::getUserId, UserContext.getUserId()));
        if (merchant == null) {
            throw new BizException("当前账号不是商家");
        }
        return merchant;
    }

    /** 实体转状态 VO，subscribed 需同时满足 status=1 且未过期 */
    private MerchantAiServiceStatusVO toStatusVO(com.xs.sheepaimall.entity.MerchantAiService service) {
        boolean valid = service.getStatus() != null && service.getStatus() == 1
                && service.getExpireTime() != null && service.getExpireTime().isAfter(LocalDateTime.now());
        return MerchantAiServiceStatusVO.builder()
                .subscribed(valid)
                .status(service.getStatus())
                .startTime(service.getStartTime())
                .expireTime(service.getExpireTime())
                .aiEnabled(service.getAiEnabled())
                .build();
    }

    @Override
    public MerchantAiServiceStatusVO setAiEnabled(Integer enabled) {
        Merchant merchant = getCurrentMerchant();
        com.xs.sheepaimall.entity.MerchantAiService service = merchantAiServiceMapper.selectOne(
                new LambdaQueryWrapper<com.xs.sheepaimall.entity.MerchantAiService>()
                        .eq(com.xs.sheepaimall.entity.MerchantAiService::getMerchantId, merchant.getId()));
        boolean enable = enabled != null && enabled == 1;
        // 开启需已开通且有效期内；关闭任何时候允许
        if (enable && (service == null || service.getStatus() == null || service.getStatus() != 1
                || service.getExpireTime() == null || !service.getExpireTime().isAfter(LocalDateTime.now()))) {
            throw new BizException("智能客服未开通或已过期，无法开启AI接待");
        }
        if (service == null) {
            // 未开通记录时不创建记录（仅返回未开通状态），商家须先完成开通
            return MerchantAiServiceStatusVO.builder().subscribed(false).status(0).aiEnabled(0).build();
        }
        service.setAiEnabled(enable ? 1 : 0);
        merchantAiServiceMapper.updateById(service);
        log.info("商家AI接待开关变更 merchantId={}, aiEnabled={}", merchant.getId(), service.getAiEnabled());
        return toStatusVO(service);
    }
}
