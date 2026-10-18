package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 商品卡片"购买"操作单创建请求（仅生成待确认操作单，确认后才创建待支付订单） */
@Data
@Schema(description = "商品卡片购买操作单创建请求")
public class ChatCreateOrderOpDTO {

    @Schema(description = "商品ID（SPU ID）", example = "1001")
    @NotNull(message = "商品ID不能为空")
    private Long spuId;

    @Schema(description = "规格ID（SKU ID）", example = "1002")
    @NotNull(message = "规格ID不能为空")
    private Long skuId;

    @Schema(description = "数量", example = "1")
    private Integer quantity;

    @Schema(description = "收货地址ID", example = "1003")
    @NotNull(message = "收货地址不能为空")
    private Long addressId;

    @Schema(description = "会话ID", example = "1004")
    private Long sessionId;
}
