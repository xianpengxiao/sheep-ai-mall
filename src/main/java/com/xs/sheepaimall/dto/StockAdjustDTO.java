package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 库存调整请求 */
@Data
@Schema(description = "库存调整请求")
public class StockAdjustDTO {

    @Schema(description = "SKU ID")
    @NotNull(message = "SKU ID不能为空")
    private Long skuId;

    @Schema(description = "调整量：正数入库，负数出库")
    @NotNull(message = "调整数量不能为空")
    private Integer delta;
}
