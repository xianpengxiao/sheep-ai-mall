package com.xs.sheepaimall.consumer;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.rabbitmq.client.Channel;
import com.xs.sheepaimall.common.RabbitMQConstants;
import com.xs.sheepaimall.dto.ChatMQMessage;
import com.xs.sheepaimall.entity.ChatSession;
import com.xs.sheepaimall.entity.ChatUserProfile;
import com.xs.sheepaimall.mapper.ChatSessionMapper;
import com.xs.sheepaimall.mapper.ChatUserProfileMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import org.springframework.data.redis.RedisConnectionFailureException;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;

/** 客服画像更新消费者 */
@Slf4j
@Component
public class ChatProfileConsumer {

    @Autowired
    private ChatUserProfileMapper chatUserProfileMapper;

    @Autowired
    private ChatSessionMapper chatSessionMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @RabbitListener(queues = RabbitMQConstants.CHAT_PROFILE_QUEUE, ackMode = "MANUAL")
    public void handleProfileUpdate(ChatMQMessage message, Channel channel,
                                     @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            // 幂等检查（Redis 不可用时跳过，直接处理）
            if (!checkIdempotent(message.getMessageId())) {
                channel.basicAck(deliveryTag, false);
                return;
            }

            Long userId = message.getUserId();
            LocalDateTime now = LocalDateTime.now();

            // 查现有画像
            ChatUserProfile profile = chatUserProfileMapper.selectOne(
                    new LambdaQueryWrapper<ChatUserProfile>()
                            .eq(ChatUserProfile::getUserId, userId)
            );

            if (profile == null) {
                // 新建画像
                profile = new ChatUserProfile();
                profile.setUserId(userId);
                profile.setTotalMessages(1);
                profile.setTotalSessions(0);
                profile.setLastActiveAt(now);
                profile.setSummary("");
                chatUserProfileMapper.insert(profile);
            } else {
                // 更新画像
                LambdaUpdateWrapper<ChatUserProfile> wrapper = new LambdaUpdateWrapper<ChatUserProfile>()
                        .eq(ChatUserProfile::getUserId, userId)
                        .set(ChatUserProfile::getLastActiveAt, now)
                        .setSql("total_messages = total_messages + 1");

                // 如果是新会话（session 的第一条消息），增加会话计数
                if (message.getSessionId() != null) {
                    ChatSession session = chatSessionMapper.selectById(message.getSessionId());
                    if (session != null) {
                        // 检查是否是第一条消息（创建时间很接近则认为是新会话）
                        long messageCount = chatSessionMapper.selectCount(
                                new LambdaQueryWrapper<ChatSession>()
                                        .eq(ChatSession::getUserId, userId)
                        );
                        wrapper.set(ChatUserProfile::getTotalSessions, (int) messageCount);
                    }
                }
                chatUserProfileMapper.update(null, wrapper);
            }

            // 定期更新画像摘要（每10条消息更新一次）
            if (profile.getTotalMessages() != null && profile.getTotalMessages() % 10 == 0) {
                updateProfileSummary(userId, profile);
            }

            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("画像更新失败 messageId={}", message.getMessageId(), e);
            try {
                // 不重试，直接 ACK 避免阻塞队列
                channel.basicAck(deliveryTag, false);
            } catch (IOException ex) {
                log.error("ACK 失败", ex);
            }
        }
    }

    /** 幂等检查：Redis 不可用时返回 true（跳过去重，直接处理） */
    private boolean checkIdempotent(String messageId) {
        try {
            String key = RabbitMQConstants.CHAT_IDEMPOTENT_PREFIX + messageId;
            Boolean success = stringRedisTemplate.opsForValue()
                    .setIfAbsent(key, "1", Duration.ofSeconds(RabbitMQConstants.IDEMPOTENT_TTL_SECONDS));
            if (Boolean.FALSE.equals(success)) {
                log.info("画像更新消息重复，跳过 messageId={}", messageId);
                return false;
            }
            return true;
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis 不可用，跳过去重直接处理 messageId={}", messageId);
            return true;
        }
    }

    /** 生成用户画像摘要 */
    private void updateProfileSummary(Long userId, ChatUserProfile profile) {
        try {
            // V1 版本：基于统计数据生成简单摘要，后续可接入 AI 生成
            StringBuilder summary = new StringBuilder();
            summary.append("该用户累计发送").append(profile.getTotalMessages()).append("条消息");
            summary.append("，参与").append(profile.getTotalSessions()).append("个会话。");

            if (profile.getLastActiveAt() != null) {
                summary.append("最近活跃时间：").append(profile.getLastActiveAt().toLocalDate());
            }

            chatUserProfileMapper.update(null,
                    new LambdaUpdateWrapper<ChatUserProfile>()
                            .eq(ChatUserProfile::getUserId, userId)
                            .set(ChatUserProfile::getSummary, summary.toString())
            );
        } catch (Exception e) {
            log.error("更新画像摘要失败 userId={}", userId, e);
        }
    }
}
