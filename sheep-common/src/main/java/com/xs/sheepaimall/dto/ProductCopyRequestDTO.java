package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 商品文案生成请求 */
@Data
@Schema(description = "商品文案生成请求")
public class ProductCopyRequestDTO {

    @NotBlank(message = "商品名称不能为空")
    @Schema(description = "商品名称", example = "智能无线蓝牙耳机 Pro Max")
    private String productName;

    @NotBlank(message = "核心卖点不能为空")
    @Schema(description = "核心卖点（多个卖点用逗号或换行分隔）", example = "主动降噪, 40小时续航, IPX5防水")
    private String coreSellingPoints;
}
