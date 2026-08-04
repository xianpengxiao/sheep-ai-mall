package com.xs.sheepaimall.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 日/月对账汇总VO */
@Data
@Schema(description = "对账汇总")
public class DailyReportVO {

    @Schema(description = "统计日期")
    private LocalDate statDate;

    @Schema(description = "总交易金额")
    private BigDecimal totalAmount;

    @Schema(description = "总佣金收入")
    private BigDecimal totalCommission;

    @Schema(description = "总提现支出")
    private BigDecimal totalWithdraw;

    @Schema(description = "总退款金额")
    private BigDecimal totalRefund;

    @Schema(description = "平台净收入")
    private BigDecimal netIncome;

    @Schema(description = "订单数")
    private Integer orderCount;
}
