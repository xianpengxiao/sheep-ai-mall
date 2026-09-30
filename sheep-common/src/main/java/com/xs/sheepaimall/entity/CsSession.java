package com.xs.sheepaimall.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 客服会话 */
@Data
@TableName("cs_session")
@Schema(description = "客服会话")
public class CsSession {

    @Schema(description = "会话ID")
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "客服ID，0=AI")
    private Long agentId;

    @Schema(description = "类型：0=AI 1=人工")
    private Integer type;

    @Schema(description = "状态：0=进行中 1=已结束 2=排队中")
    private Integer status;

    @Schema(description = "会话标题")
    private String title;

    @Schema(description = "最后消息时间")
    private LocalDateTime lastMessageAt;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @Schema(description = "逻辑删除")
    @TableLogic
    private Integer deleted;
}
