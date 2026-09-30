package com.xs.sheepaimall.config;

import com.xs.sheepaimall.common.RabbitMQConstants;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** RabbitMQ 配置 — 客服相关队列声明 */
@Configuration
public class RabbitMQConfig {

    // ==================== 交换机 ====================

    @Bean
    public TopicExchange chatExchange() {
        return new TopicExchange(RabbitMQConstants.CHAT_EXCHANGE, true, false);
    }

    // ==================== 队列 ====================

    @Bean
    public Queue chatProfileQueue() {
        return QueueBuilder.durable(RabbitMQConstants.CHAT_PROFILE_QUEUE).build();
    }

    // ==================== 绑定 ====================

    @Bean
    public Binding chatProfileBinding() {
        return BindingBuilder.bind(chatProfileQueue())
                .to(chatExchange())
                .with(RabbitMQConstants.CHAT_PROFILE_ROUTING_KEY);
    }

    // ==================== 消息转换器 ====================

    @Bean
    public Jackson2JsonMessageConverter chatMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
