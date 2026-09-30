package com.xs.sheepaimall.vo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 发送消息请求 */
@Data
@Schema(description = "发送消息请求")
public class ChatSendDTO {

    @Schema(description = "会话ID，为空则新建会话")
    private Long sessionId;

    @NotBlank(message = "消息内容不能为空")
    @Schema(description = "消息内容", required = true)
    private String content;
}
