package com.xs.sheepaimall.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 商家智能客服额度视图（坐席页内嵌消耗卡片） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "商家智能客服额度视图")
public class MerchantCsQuotaVO {

    @Schema(description = "套餐名称（未开通/未配置套餐时为 null）")
    private String planName;

    @Schema(description = "月度Token总额度（merchant_ai_service 快照，null 回退套餐值）")
    private Long monthlyTokenQuota;

    @Schema(description = "当月已用Token（Redis 实时累计）")
    private Long monthlyTokenUsed;

    @Schema(description = "当月剩余Token（额度为空时为 null）")
    private Long monthlyTokenRemain;

    @Schema(description = "今日请求数")
    private Integer todayRequestCount;

    @Schema(description = "今日已用Token")
    private Long todayTokenUsed;

    @Schema(description = "日请求上限（null 回退套餐默认）")
    private Integer dailyRequestLimit;

    @Schema(description = "日Token上限（null 回退套餐默认）")
    private Long dailyTokenLimit;
}
