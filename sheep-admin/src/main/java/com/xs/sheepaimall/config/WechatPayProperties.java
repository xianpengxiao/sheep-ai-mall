package com.xs.sheepaimall.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信支付 API v3 配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "wechat.pay")
public class WechatPayProperties {

    /** 是否启用微信支付 */
    private Boolean enabled;

    /** 商户号 */
    private String merchantId;

    /** 商户API证书序列号 */
    private String merchantSerialNumber;

    /** 商户API私钥路径（classpath 或绝对路径） */
    private String privateKeyPath;

    /** 商户API私钥内容（与 privateKeyPath 二选一） */
    private String privateKeyContent;

    /** API v3 密钥（用于回调通知解密） */
    private String apiV3Key;

    /** 支付回调通知地址 */
    private String notifyUrl;
}
