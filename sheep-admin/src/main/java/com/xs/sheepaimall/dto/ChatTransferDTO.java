package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 转人工请求 */
@Data
@Schema(description = "转人工请求")
public class ChatTransferDTO {

    @Schema(description = "会话ID")
    @NotNull(message = "会话ID不能为空")
    private Long sessionId;
}
