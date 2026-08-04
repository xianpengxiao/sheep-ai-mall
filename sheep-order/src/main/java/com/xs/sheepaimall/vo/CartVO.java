package com.xs.sheepaimall.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/** 购物车列表返回（含商品信息冗余） */
@Data
@Schema(description = "购物车项")
public class CartVO {

    @Schema(description = "购物车记录ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "会员ID")
    private Long userId;

    @Schema(description = "SPU ID")
    private Long spuId;

    @Schema(description = "SKU ID")
    private Long skuId;

    @Schema(description = "数量")
    private Integer quantity;

    @Schema(description = "是否选中：1=是 0=否")
    private Integer selected;

    @Schema(description = "商品名称")
    private String spuName;

    @Schema(description = "商品主图")
    private String spuImage;

    @Schema(description = "SKU规格名称")
    private String skuName;

    @Schema(description = "SKU图片")
    private String skuImage;

    @Schema(description = "SKU规格信息")
    private Map<String, String> specInfo;

    @Schema(description = "单价")
    private BigDecimal price;
}
