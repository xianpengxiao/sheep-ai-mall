package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 审核提现申请 */
@Data
@Schema(description = "审核提现申请")
public class WithdrawAuditDTO {

    @NotNull(message = "审核状态不能为空")
    @Schema(description = "审核状态 1待打款(通过) / 3驳回")
    private Integer status;

    @Schema(description = "驳回原因（驳回时必填）")
    private String rejectReason;
}
