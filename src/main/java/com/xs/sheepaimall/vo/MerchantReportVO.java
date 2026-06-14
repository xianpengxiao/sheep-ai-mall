package com.xs.sheepaimall.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 单店铺对账VO */
@Data
@Schema(description = "单店铺对账")
public class MerchantReportVO {

    @Schema(description = "商家ID")
    private Long merchantId;

    @Schema(description = "店铺名称")
    private String shopName;

    @Schema(description = "统计开始日期")
    private LocalDate startDate;

    @Schema(description = "统计结束日期")
    private LocalDate endDate;

    @Schema(description = "订单入账")
    private BigDecimal orderIncome;

    @Schema(description = "佣金扣除")
    private BigDecimal commissionDeduct;

    @Schema(description = "提现支出")
    private BigDecimal withdrawAmount;

    @Schema(description = "退款回冲")
    private BigDecimal refundAmount;

    @Schema(description = "可提现余额")
    private BigDecimal availableBalance;

    @Schema(description = "订单数")
    private Integer orderCount;
}
