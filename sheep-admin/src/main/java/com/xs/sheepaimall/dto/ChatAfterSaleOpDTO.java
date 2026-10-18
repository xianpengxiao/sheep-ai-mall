package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 订单卡片"申请售后"操作单创建请求（仅生成待确认操作单，确认后才创建售后单） */
@Data
@Schema(description = "订单卡片售后操作单创建请求")
public class ChatAfterSaleOpDTO {

    @Schema(description = "订单ID", example = "1001")
    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    @Schema(description = "售后类型：REFUND_ONLY=仅退款 RETURN_REFUND=退货退款 EXCHANGE=换货", example = "RETURN_REFUND")
    @NotBlank(message = "售后类型不能为空")
    private String type;

    @Schema(description = "申请原因", example = "商品有质量问题")
    private String reason;

    @Schema(description = "会话ID", example = "1004")
    private Long sessionId;
}
