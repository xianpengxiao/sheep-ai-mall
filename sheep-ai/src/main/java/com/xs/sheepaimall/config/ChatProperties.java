package com.xs.sheepaimall.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** AI 客服配置属性 */
@Data
@Component
@ConfigurationProperties(prefix = "sheep.ai.chat")
public class ChatProperties {

    /** 上下文最大历史消息数 */
    private int maxHistory = 20;

    /** AI 温度（0-1），越低越严谨 */
    private double temperature = 0.5;

    /** RAG 检索匹配文档数 */
    private int ragMatchCount = 3;

    /** 系统提示词 */
    private String systemPrompt = """
            你是一个专业、友好的电商客服助手。请根据以下信息回答用户问题：
            - 用简洁清晰的语言回复
            - 如果涉及订单、退款等敏感操作，提醒用户核实身份
            - 如果不确定答案，诚实说明并建议联系人工客服
            - 保持礼貌、耐心的服务态度
            """;
}
