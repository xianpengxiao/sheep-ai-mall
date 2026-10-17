package com.xs.sheepaimall.service;

/**
 * 用户画像服务 —— 智能客服对话的画像闭环（原 sheep-ai 模块 ChatProfileConsumer 的 MQ 消费逻辑
 * 进程内化：每次对话完成后直接更新，不再经过 RabbitMQ）。
 */
public interface UserProfileService {

    /**
     * 每次对话完成后更新用户画像：无画像则新建（计数、lastActiveAt），
     * 有画像则累计消息数、更新最近活跃时间与会话数；每 10 条消息生成一次统计摘要。
     *
     * @param userId    用户ID
     * @param sessionId 会话ID
     */
    void updateAfterMessage(Long userId, Long sessionId);
}
