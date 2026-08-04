package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/** 商品文案确认保存请求（包含生成结果） */
@Data
@Schema(description = "商品文案保存请求")
public class ProductCopySaveDTO {

    @Schema(description = "关联SPU ID（可选，0表示不关联）", example = "0")
    private Long spuId;

    @NotBlank(message = "商品名称不能为空")
    @Schema(description = "商品名称", example = "智能无线蓝牙耳机 Pro Max")
    private String productName;

    @NotBlank(message = "核心卖点不能为空")
    @Schema(description = "核心卖点原文", example = "主动降噪, 40小时续航, IPX5防水")
    private String coreSellingPoints;

    @NotBlank(message = "生成标题不能为空")
    @Schema(description = "AI生成的商品标题", example = "真无线降噪之王！XX Pro Max 震撼上市")
    private String title;

    @NotBlank(message = "生成详情不能为空")
    @Schema(description = "AI生成的商品详情描述", example = "这是一段200-300字的商品详情文案...")
    private String detail;

    @NotEmpty(message = "生成卖点列表不能为空")
    @Schema(description = "AI生成的卖点列表", example = "[\"主动降噪\", \"40小时续航\", \"IPX5防水\"]")
    private List<String> sellPoints;
}
