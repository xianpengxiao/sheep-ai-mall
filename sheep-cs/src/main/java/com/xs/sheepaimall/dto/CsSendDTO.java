package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "发送消息请求")
public class CsSendDTO {

    @Schema(description = "会话ID，为空则新建")
    private Long sessionId;

    @NotBlank(message = "消息内容不能为空")
    @Schema(description = "消息内容", required = true)
    private String content;

    @Schema(description = "消息类型：text/image/order_card/product_card")
    private String msgType = "text";

    @Schema(description = "扩展字段JSON")
    private String extra;
}
