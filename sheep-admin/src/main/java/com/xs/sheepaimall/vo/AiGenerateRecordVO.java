package com.xs.sheepaimall.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** AI文案生成记录返回 */
@Data
@Schema(description = "AI文案生成记录")
public class AiGenerateRecordVO {

    @Schema(description = "记录ID")
    private Long id;

    @Schema(description = "SPU ID")
    private Long spuId;

    @Schema(description = "商品名称")
    private String spuName;

    @Schema(description = "类型：1=商品标题 2=商品描述 3=广告文案 4=营销话术")
    private Integer type;

    @Schema(description = "类型文本")
    private String typeText;

    @Schema(description = "提示词")
    private String prompt;

    @Schema(description = "生成结果")
    private String result;

    @Schema(description = "模型名称")
    private String model;

    @Schema(description = "状态：0=处理中 1=已完成 2=失败")
    private Integer status;

    @Schema(description = "状态文本")
    private String statusText;

    @Schema(description = "失败原因")
    private String failReason;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
