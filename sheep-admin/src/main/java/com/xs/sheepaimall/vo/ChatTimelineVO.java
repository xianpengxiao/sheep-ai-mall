package com.xs.sheepaimall.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/** 聊天对象聚合时间线（单时间线展示用） */
@Data
@Schema(description = "聊天对象聚合时间线")
public class ChatTimelineVO {

    @Schema(description = "消息列表（时间正序）")
    private List<ChatMessageVO> messages;

    @Schema(description = "当前活跃会话ID（发送消息复用；无会话为 null）")
    private Long currentSessionId;

    @Schema(description = "当前活跃会话服务模式：0=AI 1=人工（无会话为 null）")
    private Integer currentSessionServiceMode;

    @Schema(description = "是否还有更早的消息")
    private Boolean hasMore;
}
