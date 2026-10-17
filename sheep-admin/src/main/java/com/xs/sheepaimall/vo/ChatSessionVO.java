package com.xs.sheepaimall.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 会话列表返回 */
@Data
@Schema(description = "会话列表项")
public class ChatSessionVO {

    @Schema(description = "会话ID")
    private Long id;

    @Schema(description = "会话标题")
    private String title;

    @Schema(description = "最后一条消息预览")
    private String lastMessage;

    @Schema(description = "最后消息时间")
    private LocalDateTime lastMessageAt;

    @Schema(description = "消息总数")
    private Integer messageCount;

    @Schema(description = "服务模式：0=AI服务 1=人工服务")
    private Integer serviceMode;

    @Schema(description = "所属商家ID（平台会话为空）")
    private Long merchantId;

    @Schema(description = "店铺名称（平台会话为空）")
    private String shopName;
}
