package com.xs.sheepaimall.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "商家搜索结果")
public class MerchantSearchVO {

    @Schema(description = "商家ID")
    private Long id;

    @Schema(description = "店铺名称（原始）")
    private String shopName;

    @Schema(description = "店铺名称（高亮）")
    private String shopNameHighlight;

    @Schema(description = "店铺logo")
    private String shopLogo;

    @Schema(description = "店铺简介（原始）")
    private String shopDesc;

    @Schema(description = "店铺简介（高亮）")
    private String shopDescHighlight;

    @Schema(description = "经营范围")
    private String businessScope;

    @Schema(description = "描述相符评分")
    private BigDecimal describeScore;

    @Schema(description = "服务态度评分")
    private BigDecimal serviceScore;

    @Schema(description = "物流服务评分")
    private BigDecimal logisticsScore;

    @Schema(description = "综合评分（三维平均）")
    private BigDecimal compositeScore;

    @Schema(description = "评价数")
    private Integer dsrCount;

    @Schema(description = "营业状态 0已打烊 1营业中")
    private Integer shopStatus;
}
