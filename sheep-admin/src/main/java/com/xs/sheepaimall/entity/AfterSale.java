package com.xs.sheepaimall.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 售后单：用户申请 → 商家处理（退款类同意时联动退款/资金/订单标记） */
@Data
@TableName("after_sale")
@Schema(description = "售后单")
public class AfterSale {

    @Schema(description = "售后单ID")
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "申请人用户ID")
    private Long userId;

    @Schema(description = "归属商家ID（从订单项反查冗余）")
    private Long merchantId;

    @Schema(description = "类型：REFUND_ONLY=仅退款 RETURN_REFUND=退货退款 EXCHANGE=换货")
    private String type;

    @Schema(description = "申请原因")
    private String reason;

    @Schema(description = "状态：PENDING=待处理 APPROVED=已完结（同意） REJECTED=已拒绝")
    private String status;

    @Schema(description = "拒绝原因")
    private String rejectReason;

    @Schema(description = "退款金额（创建时=订单实付金额）")
    private BigDecimal refundAmount;

    @Schema(description = "退款完成时间")
    private LocalDateTime refundTime;

    @Schema(description = "商家处理备注")
    private String handlerNote;

    @Schema(description = "处理时间")
    private LocalDateTime handleTime;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
