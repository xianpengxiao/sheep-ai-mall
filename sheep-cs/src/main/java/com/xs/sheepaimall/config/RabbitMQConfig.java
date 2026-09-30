package com.xs.sheepaimall.config;

import com.xs.sheepaimall.common.RabbitMQConstants;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** RabbitMQ 配置 — 客服消息拓扑 */
@Configuration
public class RabbitMQConfig {

    // ==================== 交换机 ====================

    @Bean
    public TopicExchange csExchange() {
        return new TopicExchange(RabbitMQConstants.CS_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange csDlxExchange() {
        return new TopicExchange(RabbitMQConstants.CS_DLX_EXCHANGE, true, false);
    }

    // ==================== 队列 ====================

    @Bean
    public Queue csMessagePersistQueue() {
        return QueueBuilder.durable(RabbitMQConstants.CS_MESSAGE_PERSIST_QUEUE)
                .deadLetterExchange(RabbitMQConstants.CS_DLX_EXCHANGE)
                .deadLetterRoutingKey(RabbitMQConstants.CS_MESSAGE_PERSIST_DLQ_KEY)
                .build();
    }

    @Bean
    public Queue csMessagePersistDlq() {
        return QueueBuilder.durable(RabbitMQConstants.CS_MESSAGE_PERSIST_DLQ).build();
    }

    @Bean
    public Queue csPushQueue() {
        return QueueBuilder.durable(RabbitMQConstants.CS_PUSH_QUEUE).build();
    }

    @Bean
    public Queue csQueueAssignQueue() {
        return QueueBuilder.durable(RabbitMQConstants.CS_QUEUE_ASSIGN_QUEUE).build();
    }

    @Bean
    public Queue csTransferQueue() {
        return QueueBuilder.durable(RabbitMQConstants.CS_TRANSFER_QUEUE).build();
    }

    // ==================== 绑定 ====================

    @Bean
    public Binding csMessagePersistBinding() {
        return BindingBuilder.bind(csMessagePersistQueue()).to(csExchange())
                .with(RabbitMQConstants.CS_MESSAGE_PERSIST_KEY);
    }

    @Bean
    public Binding csMessagePersistDlqBinding() {
        return BindingBuilder.bind(csMessagePersistDlq()).to(csDlxExchange())
                .with(RabbitMQConstants.CS_MESSAGE_PERSIST_DLQ_KEY);
    }

    @Bean
    public Binding csPushBinding() {
        return BindingBuilder.bind(csPushQueue()).to(csExchange())
                .with(RabbitMQConstants.CS_PUSH_KEY);
    }

    @Bean
    public Binding csQueueAssignBinding() {
        return BindingBuilder.bind(csQueueAssignQueue()).to(csExchange())
                .with(RabbitMQConstants.CS_QUEUE_ASSIGN_KEY);
    }

    @Bean
    public Binding csTransferBinding() {
        return BindingBuilder.bind(csTransferQueue()).to(csExchange())
                .with(RabbitMQConstants.CS_TRANSFER_KEY);
    }

    // ==================== 消息转换器 ====================

    @Bean
    public Jackson2JsonMessageConverter csMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(csMessageConverter());
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack && correlationData != null) {
                org.slf4j.LoggerFactory.getLogger(RabbitMQConfig.class)
                        .warn("消息发送失败 id={} cause={}", correlationData.getId(), cause);
            }
        });
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory csListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(csMessageConverter());
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(1);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
