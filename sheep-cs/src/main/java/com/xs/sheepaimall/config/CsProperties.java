package com.xs.sheepaimall.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 客服系统配置 */
@Data
@Component
@ConfigurationProperties(prefix = "sheep.cs")
public class CsProperties {

    /** 长轮询超时(ms) */
    private long pollTimeout = 30000;

    /** 消息历史最大条数 */
    private int maxHistory = 50;

    /** 排队超时(min)，超时自动取消 */
    private int queueTimeoutMinutes = 30;

    /** 队列检查间隔(ms) */
    private long queueCheckInterval = 5000;
}
