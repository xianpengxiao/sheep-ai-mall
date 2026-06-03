package com.xs.sheepaimall.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/** 订单明细返回 */
@Data
@Schema(description = "订单明细")
public class OrderItemVO {

    @Schema(description = "明细ID")
    private Long id;

    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "SPU ID")
    private Long spuId;

    @Schema(description = "SPU名称")
    private String spuName;

    @Schema(description = "SKU ID")
    private Long skuId;

    @Schema(description = "SKU名称快照")
    private String skuName;

    @Schema(description = "单价快照")
    private BigDecimal price;

    @Schema(description = "数量")
    private Integer quantity;

    @Schema(description = "小计")
    private BigDecimal totalPrice;

    @Schema(description = "商品图片快照")
    private String image;
}
