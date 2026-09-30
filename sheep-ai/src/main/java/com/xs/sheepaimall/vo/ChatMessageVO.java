package com.xs.sheepaimall.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 消息返回 */
@Data
@Schema(description = "消息")
public class ChatMessageVO {

    @Schema(description = "消息ID")
    private Long id;

    @Schema(description = "角色：0=用户 1=AI")
    private Integer role;

    @Schema(description = "消息内容")
    private String content;

    @Schema(description = "工具调用JSON")
    private String toolCalls;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
