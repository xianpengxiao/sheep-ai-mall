package com.xs.sheepaimall.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 客服消息 MQ 传输对象 */
@Data
public class CsMQMessage implements Serializable {

    /** 消息ID（幂等） */
    private String messageId;

    /** 业务消息ID */
    private Long bizMessageId;

    /** 会话ID */
    private Long sessionId;

    /** 发送者ID */
    private Long senderId;

    /** 发送者角色：0=用户 1=客服 2=AI 3=系统 */
    private Integer senderRole;

    /** 消息类型 */
    private String msgType;

    /** 文本内容 */
    private String content;

    /** 扩展字段JSON */
    private String extra;

    /** 接收方sessionId（推送用） */
    private Long targetSessionId;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 消息用途：persist / push / transfer */
    private String purpose;
}
