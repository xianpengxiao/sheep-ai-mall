package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** 商家提现申请 DTO */
@Data
@Schema(description = "商家提现申请")
public class MerchantWithdrawApplyDTO {

    @NotNull(message = "提现金额不能为空")
    @DecimalMin(value = "1.00", message = "提现金额不能小于1元")
    @DecimalMax(value = "1000000.00", message = "提现金额不能超过100万元")
    @Schema(description = "提现金额")
    private BigDecimal amount;
}
