package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 提交商品评价 */
@Data
@Schema(description = "提交商品评价")
public class ReviewDTO {

    @NotNull(message = "订单ID不能为空")
    @Schema(description = "订单ID")
    private Long orderId;

    @NotNull(message = "订单明细ID不能为空")
    @Schema(description = "订单明细ID")
    private Long orderItemId;

    @Schema(description = "商品SPU ID（可选，接口自动填充）")
    private Long spuId;

    @Schema(description = "商品SKU ID（可选，接口自动填充）")
    private Long skuId;

    @Min(value = 1, message = "评分最低1分")
    @Max(value = 5, message = "评分最高5分")
    @Schema(description = "评分 1-5")
    private Integer rating = 5;

    @Schema(description = "评价内容")
    private String content;

    @Schema(description = "评价图片URL列表")
    private List<String> imageList;
}
