package com.xs.sheepaimall.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 坐席工作台会话列表项（商家端/平台端共用） */
@Data
@Schema(description = "坐席会话")
public class CsSessionVO {

    @Schema(description = "会话ID")
    private Long sessionId;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "会话标题")
    private String title;

    @Schema(description = "服务模式：0=AI服务 1=人工服务")
    private Integer serviceMode;

    @Schema(description = "转接发起时间")
    private LocalDateTime transferTime;

    @Schema(description = "最后一条消息内容（截30字）")
    private String lastMessage;

    @Schema(description = "最后消息时间")
    private LocalDateTime lastMessageAt;

    @Schema(description = "未读数：最后一条人工/系统消息之后的用户消息条数（设计 D5）")
    private Integer unreadCount;
}
