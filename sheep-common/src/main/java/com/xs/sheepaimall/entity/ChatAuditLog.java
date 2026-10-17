package com.xs.sheepaimall.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 客服审计日志：转接/接入/转回/工具调用等行为台账，异步写入 */
@Data
@TableName("chat_audit_log")
@Schema(description = "客服审计日志")
public class ChatAuditLog {

    @Schema(description = "日志ID")
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @Schema(description = "操作者类型：USER=用户 MERCHANT=商家坐席 ADMIN=平台管理员 SYSTEM=系统")
    private String actorType;

    @Schema(description = "操作者ID")
    private Long actorId;

    @Schema(description = "会话ID")
    private Long sessionId;

    @Schema(description = "商家ID，null=平台")
    private Long merchantId;

    @Schema(description = "动作：TRANSFER_REQUEST=转接 TAKEOVER=接入 BACK_TO_AI=转回AI HUMAN_REPLY=人工回复 TOOL_CALL=工具调用")
    private String action;

    @Schema(description = "动作摘要（JSON或纯文本，≤1024字符）")
    private String detail;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
