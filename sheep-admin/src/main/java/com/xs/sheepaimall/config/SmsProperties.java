package com.xs.sheepaimall.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 腾讯云短信配置
 * <p>
 * 开发阶段无需配置，sendVerifyCode 会直接打印验证码到日志。
 * 上线前在 application-dev.yml 中填入真实密钥即可。
 * </p>
 */
@Component
@ConfigurationProperties(prefix = "tencent.sms")
public class SmsProperties {

    /** 腾讯云 SecretId（从控制台获取） */
    private String secretId = "";

    /** 腾讯云 SecretKey */
    private String secretKey = "";

    /** 短信应用 SDK AppID（从短信控制台获取） */
    private String sdkAppId = "";

    /** 短信签名（需在腾讯云短信控制台申请并通过审核） */
    private String signName = "";

    /** 短信模板 ID（需在腾讯云短信控制台申请，模板变量为 {1}） */
    private String templateId = "";

    /** 短信 Region，默认 ap-guangzhou */
    private String region = "ap-guangzhou";

    public String getSecretId() { return secretId; }
    public void setSecretId(String secretId) { this.secretId = secretId; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public String getSdkAppId() { return sdkAppId; }
    public void setSdkAppId(String sdkAppId) { this.sdkAppId = sdkAppId; }
    public String getSignName() { return signName; }
    public void setSignName(String signName) { this.signName = signName; }
    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
}
