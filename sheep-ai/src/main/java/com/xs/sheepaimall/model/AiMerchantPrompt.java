package com.xs.sheepaimall.model;

import lombok.Data;

import java.time.LocalDateTime;

/** 商家自定义提示词（PostgreSQL ai_merchant_prompt 表） */
@Data
public class AiMerchantPrompt {

    private Long id;
    private Long merchantId;
    private String rolePrompt;      // 角色设定
    private String businessRules;   // 业务规则
    private String toneStyle;       // 语气风格
    private Integer enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
