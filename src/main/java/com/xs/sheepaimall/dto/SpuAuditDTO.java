package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 商品审核入参 */
@Data
@Schema(description = "商品审核入参")
public class SpuAuditDTO {

    @NotNull(message = "商品ID不能为空")
    @Schema(description = "SPU ID")
    private Long spuId;

    @NotNull(message = "审核状态不能为空")
    @Schema(description = "审核状态 1通过 2驳回")
    private Integer auditStatus;

    @Schema(description = "驳回原因（审核驳回时必填）")
    private String auditMsg;
}
