package com.xs.sheepaimall.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** AI 模型配置（PostgreSQL ai_model_config 表） */
@Data
public class AiModelConfig {

    private Long id;
    private String ownerType;     // platform / merchant
    private Long ownerId;         // 0=平台, 非0=merchant.id
    private String provider;      // deepseek / openai / qwen / zhipu
    private String model;         // 模型名
    private String baseUrl;
    private String apiKey;
    private String embeddingModel;
    private String embeddingBaseUrl;
    private String embeddingApiKey;
    private BigDecimal temperature;
    private Integer maxTokens;
    private Integer enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 缓存 Key */
    public String cacheKey() {
        return ownerType + ":" + ownerId;
    }
}
