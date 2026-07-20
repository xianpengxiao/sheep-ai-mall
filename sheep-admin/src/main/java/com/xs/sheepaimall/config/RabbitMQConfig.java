package com.xs.sheepaimall.config;

import com.xs.sheepaimall.common.RabbitMQConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置 —— 库存锁定额与释放的交换机/队列/绑定，以及消息可靠性保障
 */
@Configuration
public class RabbitMQConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQConfig.class);

    // ==================== 交换机 ====================

    @Bean
    public TopicExchange stockExchange() {
        return ExchangeBuilder.topicExchange(RabbitMQConstants.STOCK_EXCHANGE)
                .durable(true).build();
    }

    @Bean
    public TopicExchange stockDlxExchange() {
        return ExchangeBuilder.topicExchange(RabbitMQConstants.STOCK_DLX_EXCHANGE)
                .durable(true).build();
    }

    // ==================== 队列：库存锁定 ====================

    @Bean
    public Queue stockLockQueue() {
        return QueueBuilder.durable(RabbitMQConstants.STOCK_LOCK_QUEUE)
                .deadLetterExchange(RabbitMQConstants.STOCK_DLX_EXCHANGE)
                .deadLetterRoutingKey(RabbitMQConstants.STOCK_LOCK_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue stockLockDlq() {
        return QueueBuilder.durable(RabbitMQConstants.STOCK_LOCK_DLQ).build();
    }

    @Bean
    public Binding stockLockBinding() {
        return BindingBuilder.bind(stockLockQueue())
                .to(stockExchange())
                .with(RabbitMQConstants.STOCK_LOCK_ROUTING_KEY);
    }

    @Bean
    public Binding stockLockDlxBinding() {
        return BindingBuilder.bind(stockLockDlq())
                .to(stockDlxExchange())
                .with(RabbitMQConstants.STOCK_LOCK_DLQ_ROUTING_KEY);
    }

    // ==================== 队列：库存释放 ====================

    @Bean
    public Queue stockReleaseQueue() {
        return QueueBuilder.durable(RabbitMQConstants.STOCK_RELEASE_QUEUE)
                .deadLetterExchange(RabbitMQConstants.STOCK_DLX_EXCHANGE)
                .deadLetterRoutingKey(RabbitMQConstants.STOCK_RELEASE_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue stockReleaseDlq() {
        return QueueBuilder.durable(RabbitMQConstants.STOCK_RELEASE_DLQ).build();
    }

    @Bean
    public Binding stockReleaseBinding() {
        return BindingBuilder.bind(stockReleaseQueue())
                .to(stockExchange())
                .with(RabbitMQConstants.STOCK_RELEASE_ROUTING_KEY);
    }

    @Bean
    public Binding stockReleaseDlxBinding() {
        return BindingBuilder.bind(stockReleaseDlq())
                .to(stockDlxExchange())
                .with(RabbitMQConstants.STOCK_RELEASE_DLQ_ROUTING_KEY);
    }

    // ==================== 消息转换器（JSON，含 JavaTimeModule） ====================

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // ==================== RabbitTemplate（生产者确认 + 路由失败回调） ====================

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());

        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (correlationData == null) return;
            if (ack) {
                log.info("消息确认成功 messageId={}", correlationData.getId());
            } else {
                log.error("消息确认失败 messageId={} cause={}", correlationData.getId(), cause);
            }
        });

        template.setReturnsCallback(returned -> {
            String msgId = returned.getMessage().getMessageProperties().getMessageId();
            log.error("消息路由失败 messageId={} replyCode={} replyText={} exchange={} routingKey={}",
                    msgId, returned.getReplyCode(), returned.getReplyText(),
                    returned.getExchange(), returned.getRoutingKey());
        });

        return template;
    }

    // ==================== 监听容器工厂（手动 Ack + 预取1） ====================

    @Bean
    public RabbitListenerContainerFactory<SimpleMessageListenerContainer> rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        var factory = new org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter());
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(1);
        factory.setDefaultRequeueRejected(false); // nack 不 requeue 时走 DLX
        return factory;
    }
}
