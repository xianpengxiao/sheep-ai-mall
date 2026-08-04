package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** AI文案生成请求 */
@Data
@Schema(description = "AI文案生成请求")
public class AiGenerateDTO {

    @Schema(description = "SPU ID")
    private Long spuId;

    @Schema(description = "生成类型：1=商品标题 2=商品描述 3=广告文案 4=营销话术")
    private Integer type;

    @Schema(description = "提示词")
    private String prompt;

    @Schema(description = "模型名称")
    private String model;
}
