package com.xs.sheepaimall.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 商品搜索结果 */
@Data
@Schema(description = "商品搜索结果")
public class ProductSearchVO {

    @Schema(description = "SPU ID")
    private Long id;

    @Schema(description = "商品名称（原始）")
    private String name;

    @Schema(description = "商品名称（高亮片段，含<em>标签）")
    private String nameHighlight;

    @Schema(description = "副标题（原始）")
    private String subTitle;

    @Schema(description = "副标题（高亮片段）")
    private String subTitleHighlight;

    @Schema(description = "品牌")
    private String brand;

    @Schema(description = "主图URL")
    private String mainImage;

    @Schema(description = "分类ID")
    private Long categoryId;

    @Schema(description = "分类名称")
    private String categoryName;

    @Schema(description = "最低售价")
    private BigDecimal minPrice;

    @Schema(description = "最高售价")
    private BigDecimal maxPrice;

    @Schema(description = "销量")
    private Integer salesCount;

    @Schema(description = "上架时间")
    private LocalDateTime createTime;
}
