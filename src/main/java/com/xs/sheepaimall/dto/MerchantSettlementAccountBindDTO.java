package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 商家绑定结算账户 DTO */
@Data
@Schema(description = "商家绑定结算账户")
public class MerchantSettlementAccountBindDTO {

    @NotBlank(message = "账户类型不能为空")
    @Schema(description = "账户类型 BANK/ALIPAY/WECHAT")
    private String accountType;

    @NotBlank(message = "开户人不能为空")
    @Schema(description = "开户人")
    private String accountHolder;

    @Schema(description = "银行卡号（BANK时必填）")
    private String cardNumber;

    @Schema(description = "支付宝账号（ALIPAY时必填）")
    private String alipayAccount;

    @Schema(description = "微信账号（WECHAT时必填）")
    private String wechatAccount;

    @Schema(description = "开户行")
    private String bankName;

    @Schema(description = "支行")
    private String branchBankName;
}
