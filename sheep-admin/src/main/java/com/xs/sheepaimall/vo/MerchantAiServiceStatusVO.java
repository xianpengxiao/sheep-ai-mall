package com.xs.sheepaimall.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 商家智能客服服务开通状态 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "商家智能客服服务开通状态")
public class MerchantAiServiceStatusVO {

    @Schema(description = "是否已开通且处于有效期内")
    private boolean subscribed;

    @Schema(description = "服务状态：0=未开通 1=有效 2=已过期")
    private Integer status;

    @Schema(description = "开通时间")
    private LocalDateTime startTime;

    @Schema(description = "有效期至")
    private LocalDateTime expireTime;

    @Schema(description = "AI自动接待开关：0=人工接待（默认） 1=AI接待（商家手动开启），仅开通有效期内可开启")
    private Integer aiEnabled;

    @Schema(description = "平台AI总开关：0=已关闭（商家开关被覆盖，前端应置灰） 1=开启中")
    private Integer globalAiEnabled;
}
