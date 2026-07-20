package com.xs.sheepaimall.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 资金流水VO */
@Data
@Schema(description = "资金流水")
public class FundFlowVO {

    @Schema(description = "流水ID")
    private Long id;

    @Schema(description = "流水号")
    private String flowNo;

    @Schema(description = "商家ID")
    private Long merchantId;

    @Schema(description = "店铺名称")
    private String shopName;

    @Schema(description = "流水类型")
    private String flowType;

    @Schema(description = "流水类型文本")
    private String flowTypeText;

    @Schema(description = "方向 INCOME/EXPENSE")
    private String direction;

    @Schema(description = "金额")
    private BigDecimal amount;

    @Schema(description = "业务类型")
    private String bizType;

    @Schema(description = "业务单号ID")
    private Long bizId;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
