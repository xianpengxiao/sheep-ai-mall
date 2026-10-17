package com.xs.sheepaimall.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 客服逐请求 Token 计量记录：一次 AI 调用一行，异步写入 */
@Data
@TableName("cs_usage_record")
@Schema(description = "客服Token计量记录")
public class CsUsageRecord {

    @Schema(description = "记录ID")
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "商家ID，null=平台会话")
    private Long merchantId;

    @Schema(description = "会话ID")
    private Long sessionId;

    @Schema(description = "输入Token数")
    private Integer inputTokens;

    @Schema(description = "输出Token数")
    private Integer outputTokens;

    @Schema(description = "总Token数")
    private Integer totalTokens;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
