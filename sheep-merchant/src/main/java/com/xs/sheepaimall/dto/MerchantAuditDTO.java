package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 商家入驻审核 */
@Data
@Schema(description = "商家入驻审核")
public class MerchantAuditDTO {

    @NotNull(message = "审核状态不能为空")
    @Schema(description = "审核状态 1通过 2驳回")
    private Integer status;

    @Schema(description = "驳回原因（驳回时必填）")
    private String auditRemark;
}
