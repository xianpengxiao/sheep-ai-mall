package com.xs.sheepaimall.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** AI文案生成记录 */
@Data
@TableName("ai_generate_record")
public class AiGenerateRecord {

    @Schema(description = "记录ID")
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @Schema(description = "SPU ID")
    private Long spuId;

    @Schema(description = "类型：1=商品标题 2=商品描述 3=广告文案 4=营销话术")
    private Integer type;

    @Schema(description = "提示词")
    private String prompt;

    @Schema(description = "生成结果")
    private String result;

    @Schema(description = "模型名称")
    private String model;

    @Schema(description = "状态：0=处理中 1=已完成 2=失败")
    private Integer status;

    @Schema(description = "失败原因")
    private String failReason;

    @Schema(description = "逻辑删除：0=否 1=是")
    @TableLogic
    private Integer deleted;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
