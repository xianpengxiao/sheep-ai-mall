package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 坐席人工回复 */
@Data
@Schema(description = "坐席人工回复")
public class CsReplyDTO {

    @Schema(description = "回复内容")
    @NotBlank(message = "回复内容不能为空")
    private String content;
}
