package com.xs.sheepaimall.service;

import com.xs.sheepaimall.dto.MerchantPaymentNotifyDTO;
import com.xs.sheepaimall.vo.MerchantAiServiceStatusVO;
import com.xs.sheepaimall.vo.MerchantCsQuotaVO;

/**
 * 商家智能客服服务：开通/续费、状态查询、支付回调。
 * <p>当前支付为假支付（MOCK 渠道直接成功），后续接入真实支付渠道时
 * 在 {@link #handlePaymentNotify(MerchantPaymentNotifyDTO)} 中验签并更新流水状态。</p>
 */
public interface MerchantAiService {

    /**
     * 开通或续费智能客服服务（幂等：未开通则开通，已开通则续费顺延一年）。
     * <p> renew 已并入本方法，不再单独提供续费接口。整个流程在一个本地事务中：
     * 生成支付流水、upsert 开通记录、初始化商家专属向量表。</p>
     *
     * @return 最新的开通状态
     */
    MerchantAiServiceStatusVO subscribe();

    /** 查询当前登录商家的开通状态与有效期 */
    MerchantAiServiceStatusVO getStatus();

    /**
     * 手动开启/关闭 AI 自动接待（默认人工策略）：开启需已开通且有效期内，关闭随时允许。
     * 关闭后新进入的商家会话按人工接待处理，存量 AI 会话在下一条用户消息时切回人工。
     *
     * @param enabled 1=开启AI接待 0=关闭（人工接待）
     * @return 最新开通状态
     */
    MerchantAiServiceStatusVO setAiEnabled(Integer enabled);

    /**
     * 查询当前登录商家的额度视图（坐席页内嵌消耗卡片）：
     * 套餐、月度总额/已用/剩余、今日请求数与Token、日限配置。
     * <p>已用取 Redis 实时累计键（MeteringService 写入，cs:quota:mm:/{yyyyMM} 与
     * cs:quota:m:/{yyyyMMdd}），Redis 异常降级为 0 并 warn。</p>
     */
    MerchantCsQuotaVO getQuota();

    /**
     * 支付回调（TODO 桩）：接入真实支付后在此验签并更新流水状态。
     * 当前假支付直接成功，无需回调，本方法仅打印日志预留形状。
     */
    void handlePaymentNotify(MerchantPaymentNotifyDTO dto);
}
