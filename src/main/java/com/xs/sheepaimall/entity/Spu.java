package com.xs.sheepaimall.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 商品SPU */
@Data
@TableName("spu")
public class Spu {

    @Schema(description = "SPU ID")
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @Schema(description = "所属分类ID")
    private Long categoryId;

    @Schema(description = "商家ID")
    private Long merchantId;

    @Schema(description = "商品名称")
    private String name;

    @Schema(description = "副标题")
    private String subTitle;

    @Schema(description = "品牌")
    private String brand;

    @Schema(description = "商品描述")
    private String description;

    @Schema(description = "主图URL")
    private String mainImage;

    @Schema(description = "图片列表JSON")
    private String imageList;

    @Schema(description = "状态：1=上架 0=下架")
    private Integer status;

    @Schema(description = "销量")
    private Integer salesCount;

    @Schema(description = "营业状态 0已打烊 1营业中")
    @TableField(exist = false)
    private Integer shopStatus;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
