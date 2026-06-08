package com.xs.sheepaimall.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "SKU库存明细")
public class SkuStockVO {

    @Schema(description = "SKU ID")
    private Long skuId;

    @Schema(description = "规格名称")
    private String skuName;

    @Schema(description = "单价")
    private BigDecimal price;

    @Schema(description = "库存数")
    private Integer stock;
}
