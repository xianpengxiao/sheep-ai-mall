package com.xs.sheepaimall.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 资金流水 */
@Data
@TableName("fund_flow")
public class FundFlow {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String flowNo;

    /** 商家ID（NULL=平台流水） */
    private Long merchantId;

    /** commission_income / subsidy / refund_deduct / withdraw_expense / order_income / withdraw_refund */
    private String flowType;

    /** INCOME / EXPENSE */
    private String direction;

    private BigDecimal amount;

    /** 操作前余额 */
    private BigDecimal balanceBefore;

    /** 操作后余额 */
    private BigDecimal balanceAfter;

    /** order / withdraw / refund */
    private String bizType;

    /** 业务单号ID */
    private Long bizId;

    private String remark;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
