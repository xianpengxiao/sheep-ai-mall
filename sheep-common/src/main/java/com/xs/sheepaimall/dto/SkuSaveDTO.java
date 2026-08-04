package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/** 商品SKU新增/编辑请求 */
@Data
@Schema(description = "商品SKU新增/编辑请求")
public class SkuSaveDTO {

    @Schema(description = "SKU ID，编辑时传入")
    private Long id;

    @Schema(description = "所属SPU ID")
    private Long spuId;

    @Schema(description = "SKU编码")
    @NotBlank(message = "SKU编码不能为空")
    private String skuCode;

    @Schema(description = "规格名称")
    @NotBlank(message = "规格名称不能为空")
    private String skuName;

    @Schema(description = "规格信息，如 {\"颜色\":\"红色\",\"尺寸\":\"XL\"}")
    private Map<String, String> specInfo;

    @Schema(description = "单价")
    @NotNull(message = "价格不能为空")
    @Min(value = 0, message = "价格不能为负数")
    private BigDecimal price;

    @Schema(description = "库存数量")
    @NotNull(message = "库存不能为空")
    @Min(value = 0, message = "库存不能为负数")
    private Integer stock;

    @Schema(description = "图片URL")
    private String image;

    @Schema(description = "状态：1=启用 0=禁用")
    private Integer status;
}
