package com.xs.sheepaimall.service.agent;

import lombok.Data;

/**
 * 单次 AI 调用的 Token 计量（M6 末帧 metadata 的 Usage 提取结果）。
 * <p>由 AgentChatService 流式末帧回填，ChatServiceImpl 在 doOnComplete 时读取并交 MeteringService 落账。</p>
 */
@Data
public class ChatMeter {

    /** 输入（prompt）Token 数 */
    private int inputTokens;

    /** 输出（completion）Token 数 */
    private int outputTokens;

    /** 总 Token 数 */
    private int totalTokens;
}
