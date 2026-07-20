package com.xs.sheepaimall.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 商家提现申请 */
@Data
@TableName("merchant_withdraw")
public class MerchantWithdraw {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long merchantId;

    private String withdrawNo;

    /** 提现金额 */
    private BigDecimal amount;

    /** 手续费 */
    private BigDecimal fee;

    /** 实际到账 */
    private BigDecimal actualAmount;

    /** 账户类型 */
    private String accountType;

    /** 脱敏后的账户信息 */
    private String accountInfo;

    /** 0待审核 1待打款 2已打款 3已驳回 */
    private Integer status;

    private String rejectReason;

    private Long auditUserId;

    private LocalDateTime auditTime;

    private LocalDateTime finishTime;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
