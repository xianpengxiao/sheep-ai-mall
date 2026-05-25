package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 商品SPU查询条件 */
@Data
@Schema(description = "商品SPU查询条件")
public class SpuQueryDTO {

    @Schema(description = "分类ID筛选")
    private Long categoryId;

    @Schema(description = "关键词搜索（商品名称）")
    private String keyword;

    @Schema(description = "状态筛选：1=上架 0=下架")
    private Integer status;

    @Schema(description = "排序字段：sales_count / create_time")
    private String orderBy;

    @Schema(description = "页码", example = "1")
    private Integer pageNum = 1;

    @Schema(description = "每页条数", example = "10")
    private Integer pageSize = 10;
}
