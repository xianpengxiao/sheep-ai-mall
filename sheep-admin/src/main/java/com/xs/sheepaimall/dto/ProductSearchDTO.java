package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/** 商品搜索请求 */
@Data
@Schema(description = "商品搜索请求")
public class ProductSearchDTO {

    @Schema(description = "搜索关键词", example = "蓝牙耳机")
    private String keyword;

    @Schema(description = "分类ID 筛选", example = "1")
    private Long categoryId;

    @Schema(description = "最低价格", example = "99.00")
    private BigDecimal minPrice;

    @Schema(description = "最高价格", example = "999.00")
    private BigDecimal maxPrice;

    @Schema(description = "排序方式：relevance=相关度 salesDesc=销量降序 priceAsc=价格升序 priceDesc=价格降序 newest=最新",
            example = "salesDesc")
    private String sortBy;

    @Schema(description = "页码（从1开始）", example = "1")
    private Integer pageNum = 1;

    @Schema(description = "每页条数", example = "20")
    private Integer pageSize = 20;
}
