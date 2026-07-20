package com.xs.sheepaimall.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 商家结算账户VO（脱敏展示） */
@Data
@Schema(description = "商家结算账户")
public class SettlementAccountVO {

    @Schema(description = "账户ID")
    private Long id;

    @Schema(description = "商家ID")
    private Long merchantId;

    @Schema(description = "店铺名称")
    private String shopName;

    @Schema(description = "账户类型 BANK/ALIPAY/WECHAT")
    private String accountType;

    @Schema(description = "开户人")
    private String accountHolder;

    @Schema(description = "银行卡号（脱敏）")
    private String cardNumber;

    @Schema(description = "支付宝账号（脱敏）")
    private String alipayAccount;

    @Schema(description = "微信账号（脱敏）")
    private String wechatAccount;

    @Schema(description = "开户行")
    private String bankName;

    @Schema(description = "结算费率(%)")
    private BigDecimal settlementRate;

    @Schema(description = "结算周期 T+1/T+7")
    private String settlementCycle;

    @Schema(description = "提现权限 0禁用 1启用")
    private Integer withdrawEnabled;

    @Schema(description = "绑定状态 0未绑定 1已绑定")
    private Integer bindingStatus;

    @Schema(description = "当前可提现余额")
    private BigDecimal balance;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
