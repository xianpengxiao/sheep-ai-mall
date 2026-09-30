package com.xs.sheepaimall.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rabbitmq.client.Channel;
import com.xs.sheepaimall.common.RabbitMQConstants;
import com.xs.sheepaimall.core.CsMessageTableNameHandler;
import com.xs.sheepaimall.dto.CsMQMessage;
import com.xs.sheepaimall.entity.CsMessage;
import com.xs.sheepaimall.entity.CsSession;
import com.xs.sheepaimall.mapper.CsMessageMapper;
import com.xs.sheepaimall.mapper.CsSessionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 消息异步落库（Redis先写 → MQ消费 → MySQL持久化） */
@Slf4j
@Component
public class MessagePersistConsumer {

    private static final String CS_MSG_DEDUP_PREFIX = RabbitMQConstants.CS_MSG_IDEMPOTENT_PREFIX;

    @Autowired
    private CsMessageMapper csMessageMapper;

    @Autowired
    private CsSessionMapper csSessionMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @RabbitListener(queues = RabbitMQConstants.CS_MESSAGE_PERSIST_QUEUE, containerFactory = "csListenerContainerFactory")
    public void handlePersist(CsMQMessage msg, Channel channel,
                               @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            // 幂等检查
            String dedupKey = CS_MSG_DEDUP_PREFIX + msg.getMessageId();
            try {
                Boolean ok = stringRedisTemplate.opsForValue()
                        .setIfAbsent(dedupKey, "1", Duration.ofSeconds(86400));
                if (Boolean.FALSE.equals(ok)) {
                    channel.basicAck(deliveryTag, false);
                    return;
                }
            } catch (RedisConnectionFailureException e) {
                // Redis 不可用，跳过去重
            }

            // 写入 MySQL（按月分表）
            CsMessageTableNameHandler.setMonth(msg.getCreateTime());
            try {
                CsMessage entity = new CsMessage();
                entity.setSessionId(msg.getSessionId());
                entity.setSenderId(msg.getSenderId());
                entity.setSenderRole(msg.getSenderRole());
                entity.setMsgType(msg.getMsgType());
                entity.setContent(msg.getContent());
                entity.setExtra(msg.getExtra());
                entity.setCreateTime(msg.getCreateTime());
                csMessageMapper.insert(entity);
            } finally {
                CsMessageTableNameHandler.clearMonth();
            }

            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("消息落库失败 messageId={}", msg.getMessageId(), e);
            retryOrDlq(msg, channel, deliveryTag);
        }
    }

    private void retryOrDlq(CsMQMessage msg, Channel channel, long deliveryTag) {
        try {
            // 简单的重试策略：直接NACK不进DLQ（让DLX处理重试）
            channel.basicNack(deliveryTag, false, false);
        } catch (Exception ex) {
            log.error("NACK失败", ex);
        }
    }
}
