package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 确认待执行操作请求（只带 operationId + approve，不带业务参数） */
@Data
@Schema(description = "确认待执行操作请求")
public class ConfirmDTO {

    @NotBlank(message = "操作单ID不能为空")
    @Schema(description = "操作单ID", required = true)
    private String operationId;

    @NotNull(message = "approve不能为空")
    @Schema(description = "是否确认执行 true=执行 false=取消", required = true)
    private Boolean approve;
}
