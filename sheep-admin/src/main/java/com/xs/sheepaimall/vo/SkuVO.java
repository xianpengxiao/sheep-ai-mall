package com.xs.sheepaimall.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/** 商品SKU返回 */
@Data
@Schema(description = "商品SKU信息")
public class SkuVO {

    @Schema(description = "SKU ID")
    private Long id;

    @Schema(description = "所属SPU ID")
    private Long spuId;

    @Schema(description = "SKU编码")
    private String skuCode;

    @Schema(description = "规格名称")
    private String skuName;

    @Schema(description = "规格信息")
    private Map<String, String> specInfo;

    @Schema(description = "单价")
    private BigDecimal price;

    @Schema(description = "库存数量")
    private Integer stock;

    @Schema(description = "图片URL")
    private String image;

    @Schema(description = "状态：1=启用 0=禁用")
    private Integer status;
}
