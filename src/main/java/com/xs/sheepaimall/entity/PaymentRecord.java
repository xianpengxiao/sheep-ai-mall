package com.xs.sheepaimall.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付记录 —— 追踪每笔支付的生命周期
 */
@Data
@TableName("payment_record")
@Schema(description = "支付记录")
public class PaymentRecord {

    @Schema(description = "记录ID")
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "订单编号")
    private String orderNo;

    @Schema(description = "会员ID")
    private Long userId;

    @Schema(description = "微信支付交易ID（支付成功后微信返回）")
    private String transactionId;

    @Schema(description = "微信预支付ID（prepay_id）")
    private String prepayId;

    @Schema(description = "实付金额")
    private BigDecimal payAmount;

    @Schema(description = "支付方式：WECHAT_JSAPI / WECHAT_APP / WECHAT_NATIVE")
    private String payMethod;

    @Schema(description = "状态：PENDING=待支付 SUCCESS=成功 FAILED=失败 CLOSED=已关闭 REFUNDED=已退款")
    private String status;

    @Schema(description = "支付完成时间")
    private LocalDateTime payTime;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
