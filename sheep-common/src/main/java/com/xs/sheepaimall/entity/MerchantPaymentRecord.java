package com.xs.sheepaimall.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 商家服务支付流水（当前为假支付，结构与真实支付一致） */
@Data
@TableName("merchant_payment_record")
@Schema(description = "商家服务支付流水")
public class MerchantPaymentRecord {

    @Schema(description = "流水ID")
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @Schema(description = "商家ID")
    private Long merchantId;

    @Schema(description = "业务类型：AI_SERVICE=智能客服开通")
    private String bizType;

    @Schema(description = "交易金额（元）")
    private BigDecimal amount;

    @Schema(description = "支付渠道：MOCK=假支付，预留真实渠道")
    private String channel;

    @Schema(description = "支付状态：0=待支付 1=成功 2=失败")
    private Integer status;

    @Schema(description = "商户订单号")
    private String outTradeNo;

    @Schema(description = "支付完成时间")
    private LocalDateTime payTime;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
