package com.xs.sheepaimall.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.xs.sheepaimall.vo.SkuStockVO;
import java.util.List;

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

    @Schema(description = "审核状态 0待审核 1审核通过 2审核驳回")
    private Integer auditStatus;

    @Schema(description = "审核驳回原因")
    private String auditMsg;

    @Schema(description = "审核人用户名")
    private String auditBy;

    @Schema(description = "销量")
    private Integer salesCount;

    @Schema(description = "营业状态 0已打烊 1营业中")
    @TableField(exist = false)
    private Integer shopStatus;

    @Schema(description = "最低SKU价格")
    @TableField(exist = false)
    private BigDecimal minPrice;

    @Schema(description = "所有启用SKU的库存总和")
    @TableField(exist = false)
    private Integer totalStock;

    @Schema(description = "库存状态 0已售罄 1正常 2库存紧张(≤10)")
    @TableField(exist = false)
    private Integer stockStatus;

    @Schema(description = "是否有多个规格")
    @TableField(exist = false)
    private Boolean multiSpec;

    @Schema(description = "部分规格缺货")
    @TableField(exist = false)
    private Boolean partOutOfStock;

    @Schema(description = "各SKU库存明细（悬浮提示用）")
    @TableField(exist = false)
    private List<SkuStockVO> skuStockList;

    @Schema(description = "SKU数量")
    @TableField(exist = false)
    private Integer skuCount;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
