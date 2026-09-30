package com.xs.sheepaimall.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "客服会话")
public class CsSessionVO {

    @Schema(description = "会话ID")
    private Long id;

    @Schema(description = "会话标题")
    private String title;

    @Schema(description = "类型：0=AI 1=人工")
    private Integer type;

    @Schema(description = "状态：0=进行中 1=已结束 2=排队中")
    private Integer status;

    @Schema(description = "客服名称")
    private String agentName;

    @Schema(description = "最后一条消息预览")
    private String lastMessage;

    @Schema(description = "最后消息时间")
    private LocalDateTime lastMessageAt;

    @Schema(description = "消息总数")
    private Integer messageCount;
}
