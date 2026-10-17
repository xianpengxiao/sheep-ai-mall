package com.xs.sheepaimall.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 客服待确认操作单（写工具只生成操作单，绝不直接执行，用户确认后才真正执行） */
@Data
@TableName("chat_pending_op")
@Schema(description = "客服待确认操作单")
public class ChatPendingOp {

    @Schema(description = "操作单ID（UUID）")
    @TableId(type = IdType.INPUT)
    private String id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "会话ID")
    private Long sessionId;

    @Schema(description = "操作类型 UPDATE_ADDRESS/CANCEL_ORDER/ADD_CART")
    private String opType;

    @Schema(description = "参数快照JSON")
    private String argsJson;

    @Schema(description = "给用户看的操作描述")
    private String displayText;

    @Schema(description = "状态：0=待处理 1=已执行 2=已取消")
    private Integer status;

    @Schema(description = "过期时间")
    private LocalDateTime expireTime;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
