package com.xs.sheepaimall.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 类目佣金规则 */
@Data
@TableName("commission_config")
public class CommissionConfig {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long categoryId;

    /** 佣金比例(%) */
    private BigDecimal commissionRate;

    private LocalDate effectiveDate;

    private LocalDate expireDate;

    /** 0禁用 1启用 */
    private Integer status;

    private String remark;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
