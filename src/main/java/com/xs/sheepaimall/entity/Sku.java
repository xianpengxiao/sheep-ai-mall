package com.xs.sheepaimall.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 商品SKU */
@Data
@TableName("sku")
public class Sku {

    @Schema(description = "SKU ID")
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @Schema(description = "所属SPU ID")
    private Long spuId;

    @Schema(description = "SKU编码")
    private String skuCode;

    @Schema(description = "规格名称")
    private String skuName;

    @Schema(description = "规格信息JSON")
    private String specInfo;

    @Schema(description = "单价")
    private BigDecimal price;

    @Schema(description = "库存数量")
    private Integer stock;

    @Schema(description = "图片URL")
    private String image;

    @Schema(description = "状态：1=启用 0=禁用")
    private Integer status;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
