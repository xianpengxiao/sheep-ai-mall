package com.xs.sheepaimall.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 商家结算账户 */
@Data
@TableName("merchant_settlement_account")
public class MerchantSettlementAccount {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long merchantId;

    /** BANK / ALIPAY / WECHAT */
    private String accountType;

    private String accountHolder;

    /** 银行卡号（加密存储） */
    private String cardNumber;

    private String alipayAccount;

    private String wechatAccount;

    private String bankName;

    private String branchBankName;

    /** 结算费率(%) */
    private BigDecimal settlementRate;

    /** T+1 / T+7 */
    private String settlementCycle;

    /** 0禁用 1启用 */
    private Integer withdrawEnabled;

    /** 0未绑定 1已绑定 */
    private Integer bindingStatus;

    /** 当前可提现余额 */
    private BigDecimal balance;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
