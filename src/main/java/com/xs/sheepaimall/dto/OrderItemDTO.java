package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 订单明细DTO */
@Data
@Schema(description = "订单明细")
public class OrderItemDTO {

    @Schema(description = "SPU ID")
    private Long spuId;

    @Schema(description = "SKU ID")
    private Long skuId;

    @Schema(description = "数量")
    private Integer quantity;
}
