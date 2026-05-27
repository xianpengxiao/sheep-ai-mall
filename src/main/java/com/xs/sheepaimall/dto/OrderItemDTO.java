package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 订单明细DTO */
@Data
@Schema(description = "订单明细")
public class OrderItemDTO {

    @Schema(description = "SPU ID")
    @NotNull(message = "SPU ID不能为空")
    private Long spuId;

    @Schema(description = "SKU ID")
    @NotNull(message = "SKU ID不能为空")
    private Long skuId;

    @Schema(description = "数量")
    @Min(value = 1, message = "数量必须大于0")
    private Integer quantity;
}
