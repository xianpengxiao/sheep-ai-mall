package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

/** 编辑商家结算账户（费率/周期/提现权限） */
@Data
@Schema(description = "编辑商家结算账户")
public class SettlementAccountEditDTO {

    @Schema(description = "结算费率(%)")
    @DecimalMin(value = "0.00", message = "结算费率不能小于0")
    @DecimalMax(value = "100.00", message = "结算费率不能超过100")
    private BigDecimal settlementRate;

    @Schema(description = "结算周期 T+1 / T+7")
    @Pattern(regexp = "^T\\+[17]$", message = "结算周期格式错误，应为 T+1 或 T+7")
    private String settlementCycle;

    @Schema(description = "提现权限 0禁用 1启用")
    private Integer withdrawEnabled;
}
