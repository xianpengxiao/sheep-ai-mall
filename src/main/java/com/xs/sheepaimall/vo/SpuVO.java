package com.xs.sheepaimall.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 商品SPU详情返回 */
@Data
@Schema(description = "商品SPU详情")
public class SpuVO {

    @Schema(description = "SPU ID")
    private Long id;

    @Schema(description = "所属分类ID")
    private Long categoryId;

    @Schema(description = "分类名称")
    private String categoryName;

    @Schema(description = "商家ID")
    private Long merchantId;

    @Schema(description = "店铺名称")
    private String shopName;

    @Schema(description = "店铺logo")
    private String shopLogo;

    @Schema(description = "店铺描述相符评分")
    private Double shopDescribeScore;

    @Schema(description = "店铺服务态度评分")
    private Double shopServiceScore;

    @Schema(description = "店铺物流服务评分")
    private Double shopLogisticsScore;

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

    @Schema(description = "图片URL列表")
    private List<String> imageList;

    @Schema(description = "状态：1=上架 0=下架")
    private Integer status;

    @Schema(description = "销量")
    private Integer salesCount;

    @Schema(description = "平均评分")
    private Double rating;

    @Schema(description = "评价数")
    private Integer reviewCount;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "关联的SKU列表")
    private List<SkuVO> skuList;
}
