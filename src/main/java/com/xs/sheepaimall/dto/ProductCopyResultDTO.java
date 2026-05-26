package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/** 商品文案生成结果 */
@Data
@Schema(description = "商品文案生成结果")
public class ProductCopyResultDTO {

    @Schema(description = "商品标题", example = "真无线降噪之王！XX Pro Max 震撼上市")
    private String title;

    @Schema(description = "商品详情描述", example = "这是一段200-300字的商品详情文案...")
    private String detail;

    @Schema(description = "核心卖点列表", example = "[\"主动降噪\", \"40小时超长续航\", \"IPX5级防水\"]")
    private List<String> sellPoints;
}
