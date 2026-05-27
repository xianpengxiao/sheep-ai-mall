package com.xs.sheepaimall.config;

import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationConfig;
import com.wechat.pay.java.service.payments.jsapi.JsapiService;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 微信支付 API v3 配置 —— JSAPI + Native 服务 Bean
 */
@Configuration
@ConditionalOnExpression("'${wechat.pay.enabled:false}' == 'true'")
public class WechatPayConfig {

    @Resource
    private WechatPayProperties props;

    /** 微信支付 SDK 核心配置（自动下载/更新平台证书） */
    @Bean
    public RSAAutoCertificateConfig AutowechatPayConfig() {
        // 双重保险：条件注解失效时直接跳过 Bean 创建
        if (props.getEnabled() == null || !props.getEnabled()) {
            throw new IllegalStateException(
                    "微信支付未启用（wechat.pay.enabled=false），不应创建此 Bean。" +
                    "请检查 @ConditionalOnExpression 是否生效。");
        }
        String privateKey = loadPrivateKey();
        return new RSAAutoCertificateConfig.Builder()
                .merchantId(props.getMerchantId())
                .privateKey(privateKey)
                .merchantSerialNumber(props.getMerchantSerialNumber())
                .apiV3Key(props.getApiV3Key())
                .build();
    }

    /**
     * 通知配置 —— RSAAutoCertificateConfig 适配为 NotificationConfig。
     * RSAAutoCertificateConfig 内部已持有 Verifier / AeadCipher 能力，直接委托即可。
     */
    @Bean
    public NotificationConfig notificationConfig(RSAAutoCertificateConfig config) {
        return new NotificationConfig() {
            @Override
            public com.wechat.pay.java.core.cipher.Verifier createVerifier() {
                return config.createVerifier();
            }

            @Override
            public com.wechat.pay.java.core.cipher.AeadCipher createAeadCipher() {
                return config.createAeadCipher();
            }

            @Override
            public String getSignType() {
                return "WECHATPAY2-SHA256-RSA2048";
            }

            @Override
            public String getCipherType() {
                return "AEAD_AES_256_GCM";
            }
        };
    }

    /** JSAPI 支付服务（公众号/小程序支付） */
    @Bean
    public JsapiService jsapiService(RSAAutoCertificateConfig config) {
        return new JsapiService.Builder().config(config).build();
    }

    /** Native 支付服务（扫码支付） */
    @Bean
    public NativePayService nativePayService(RSAAutoCertificateConfig config) {
        return new NativePayService.Builder().config(config).build();
    }

    /** 加载商户私钥：优先读文件路径，其次读配置内容 */
    private String loadPrivateKey() {
        String path = props.getPrivateKeyPath();
        if (path != null && !path.isBlank()) {
            try {
                // 使用完全限定名避免与 jakarta.annotation.Resource 冲突
                org.springframework.core.io.Resource res =
                        new org.springframework.core.io.ClassPathResource(path);
                if (res.exists()) {
                    return new String(res.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                }
                return Files.readString(Paths.get(path), StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new IllegalStateException("无法加载商户私钥文件: " + path, e);
            }
        }
        String content = props.getPrivateKeyContent();
        if (content != null && !content.isBlank()) {
            return content;
        }
        throw new IllegalStateException(
                "微信支付商户私钥未配置，请设置 wechat.pay.private-key-path 或 wechat.pay.private-key-content");
    }
}
