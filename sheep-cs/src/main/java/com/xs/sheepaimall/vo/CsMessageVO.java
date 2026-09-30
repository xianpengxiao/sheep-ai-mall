package com.xs.sheepaimall.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "客服消息")
public class CsMessageVO {

    @Schema(description = "消息ID")
    private Long id;

    @Schema(description = "会话ID")
    private Long sessionId;

    @Schema(description = "发送者ID")
    private Long senderId;

    @Schema(description = "发送者角色：0=用户 1=客服 2=AI 3=系统")
    private Integer senderRole;

    @Schema(description = "消息类型")
    private String msgType;

    @Schema(description = "文本内容")
    private String content;

    @Schema(description = "扩展字段JSON")
    private String extra;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
