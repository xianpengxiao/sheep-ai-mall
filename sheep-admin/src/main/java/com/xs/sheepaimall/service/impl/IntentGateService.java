package com.xs.sheepaimall.service.impl;

import com.xs.sheepaimall.config.ChatProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 意图门禁 —— 关键词白名单判定，不上 LLM（确定性、零成本，见设计 D11）。
 * <p>转人工关键词优先于一切判断；命中领域/问候词放行；未命中判为闲聊拦截（零 Token 消耗）。</p>
 */
@Service
public class IntentGateService {

    /** 判定结果 */
    public enum IntentResult {
        /** 命中转人工关键词 */
        TRANSFER_KEYWORD,
        /** 命中电商领域词 */
        DOMAIN,
        /** 命中问候词 */
        GREETING,
        /** 未命中任何词表（闲聊/跑题） */
        OFFTOPIC
    }

    @Autowired
    private ChatProperties chatProperties;

    /**
     * 判定用户消息意图（content 已非空）。
     * 黑名单思路：只有命中明确闲聊词才零成本拦截；其余一律放行给 AI（品类词无法枚举全，
     * 白名单必然误伤真实提问，如"推荐个耳机"既无领域词也无疑问词）。
     */
    public IntentResult judge(String content) {
        String text = content.toLowerCase();
        if (containsAny(text, splitKeywords(chatProperties.getTransferKeywords()))) {
            return IntentResult.TRANSFER_KEYWORD;
        }
        if (containsAny(text, splitKeywords(chatProperties.getGreetingKeywords()))) {
            return IntentResult.GREETING;
        }
        if (containsAny(text, splitKeywords(chatProperties.getChitchatKeywords()))) {
            return IntentResult.OFFTOPIC;
        }
        return IntentResult.DOMAIN;
    }

    private boolean containsAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private List<String> splitKeywords(String keywords) {
        return Arrays.stream(keywords.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
