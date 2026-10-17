package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 发送消息请求 */
@Data
@Schema(description = "发送消息请求")
public class ChatSendDTO {

    @Schema(description = "会话ID，为空则新建会话")
    private Long sessionId;

    @Schema(description = "目标商家ID（可选），仅新建会话时生效：该商家已开通智能客服则归属商家，否则按平台会话处理")
    private Long merchantId;

    @NotBlank(message = "消息内容不能为空")
    @Schema(description = "消息内容", required = true)
    private String content;
}
