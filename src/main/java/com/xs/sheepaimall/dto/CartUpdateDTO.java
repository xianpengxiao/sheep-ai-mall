package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 购物车更新请求（数量/选中状态） */
@Data
@Schema(description = "购物车更新请求")
public class CartUpdateDTO {

    @Schema(description = "购物车记录ID")
    private Long id;

    @Schema(description = "数量")
    private Integer quantity;

    @Schema(description = "是否选中：1=是 0=否")
    private Integer selected;
}
