package com.xs.sheepaimall.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 客服异步消息 DTO — 用于 RabbitMQ 传递画像更新任务 */
@Data
public class ChatMQMessage implements Serializable {

    /** 消息ID（幂等去重用） */
    private String messageId;

    /** 用户ID */
    private Long userId;

    /** 会话ID */
    private Long sessionId;

    /** 用户消息内容 */
    private String content;

    /** 创建时间 */
    private LocalDateTime createTime;
}
