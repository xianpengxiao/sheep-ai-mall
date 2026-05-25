package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 加入购物车请求 */
@Data
@Schema(description = "加入购物车请求")
public class CartAddDTO {

    @Schema(description = "会员ID")
    private Long memberId;

    @Schema(description = "SPU ID")
    private Long spuId;

    @Schema(description = "SKU ID")
    private Long skuId;

    @Schema(description = "数量")
    private Integer quantity;
}
