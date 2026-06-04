package com.xs.sheepaimall.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/** 店铺营收统计 */
@Data
@Schema(description = "店铺营收统计")
public class IncomeStatVO {

    @Schema(description = "今日销售额")
    private BigDecimal todayAmount;

    @Schema(description = "今日订单数")
    private Integer todayOrderCount;

    @Schema(description = "本月销售额")
    private BigDecimal monthAmount;

    @Schema(description = "本月订单数")
    private Integer monthOrderCount;

    @Schema(description = "累计销售额")
    private BigDecimal totalAmount;

    @Schema(description = "累计订单数")
    private Integer totalOrderCount;
}
