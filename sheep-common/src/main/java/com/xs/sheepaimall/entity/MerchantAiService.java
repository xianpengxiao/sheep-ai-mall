package com.xs.sheepaimall.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 商家智能客服服务开通记录 */
@Data
@TableName("merchant_ai_service")
@Schema(description = "商家智能客服服务开通记录")
public class MerchantAiService {

    @Schema(description = "记录ID")
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @Schema(description = "商家ID")
    private Long merchantId;

    @Schema(description = "服务状态：0=未开通 1=有效 2=已过期")
    private Integer status;

    @Schema(description = "开通时间")
    private LocalDateTime startTime;

    @Schema(description = "有效期至")
    private LocalDateTime expireTime;

    @Schema(description = "套餐ID(cs_plan)")
    private Long planId;

    @Schema(description = "月度Token额度快照，null=回退查套餐值")
    private Long monthlyTokenQuota;

    @Schema(description = "日请求上限，null=用套餐默认")
    private Integer dailyRequestLimit;

    @Schema(description = "日Token上限，null=用套餐默认")
    private Integer dailyTokenLimit;

    @Schema(description = "AI自动接待开关：0=人工接待（默认） 1=AI接待（商家手动开启）")
    private Integer aiEnabled;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
