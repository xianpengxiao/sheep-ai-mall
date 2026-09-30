package com.xs.sheepaimall.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 客服消息 */
@Data
@TableName("chat_message")
@Schema(description = "客服消息")
public class ChatMessage {

    @Schema(description = "消息ID")
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @Schema(description = "会话ID")
    private Long sessionId;

    @Schema(description = "角色：0=用户 1=AI")
    private Integer role;

    @Schema(description = "消息内容")
    private String content;

    @Schema(description = "工具调用JSON")
    private String toolCalls;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "逻辑删除：0=未删 1=已删")
    @TableLogic
    private Integer deleted;
}
