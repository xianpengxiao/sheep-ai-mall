package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 商家服务支付回调参数（通用形状，当前为假支付桩）。
 * <p>接入真实支付渠道后，各渠道回调报文统一映射到本对象再处理。</p>
 */
@Data
@Schema(description = "商家服务支付回调参数")
public class MerchantPaymentNotifyDTO {

    @Schema(description = "商户订单号（对应 merchant_payment_record.out_trade_no）")
    private String outTradeNo;

    @Schema(description = "第三方支付流水号")
    private String tradeNo;

    @Schema(description = "支付结果：SUCCESS=成功 FAIL=失败")
    private String tradeStatus;

    @Schema(description = "交易金额（元）")
    private BigDecimal totalAmount;
}
