package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 售后申请请求 */
@Data
@Schema(description = "售后申请请求")
public class AfterSaleApplyDTO {

    @Schema(description = "订单ID", example = "1001")
    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    @Schema(description = "类型：REFUND_ONLY=仅退款 RETURN_REFUND=退货退款 EXCHANGE=换货", example = "RETURN_REFUND")
    @NotBlank(message = "售后类型不能为空")
    private String type;

    @Schema(description = "申请原因", example = "商品有质量问题")
    private String reason;
}
