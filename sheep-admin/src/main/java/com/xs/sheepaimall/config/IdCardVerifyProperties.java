package com.xs.sheepaimall.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 身份证实名认证 API 配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "sheep.idcard")
public class IdCardVerifyProperties {

    /** API 地址，为空时跳过真实校验（开发模式） */
    private String apiUrl = "";

    /** 认证方式：APPCODE / Token / ApiKey */
    private String authType = "APPCODE";

    /** 认证凭据 */
    private String authKey = "";
}
