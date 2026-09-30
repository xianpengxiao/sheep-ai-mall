package com.xs.sheepaimall.consumer;

import com.rabbitmq.client.Channel;
import com.xs.sheepaimall.common.RabbitMQConstants;
import com.xs.sheepaimall.dto.CsMQMessage;
import com.xs.sheepaimall.service.CsQueueService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/** 排队分配：消费排队消息，尝试分配客服 */
@Slf4j
@Component
public class QueueAssignConsumer {

    @Autowired
    private CsQueueService csQueueService;

    @RabbitListener(queues = RabbitMQConstants.CS_QUEUE_ASSIGN_QUEUE, containerFactory = "csListenerContainerFactory")
    public void handleAssign(CsMQMessage msg, Channel channel,
                              @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            Long agentId = csQueueService.assignAgent(msg.getSessionId());
            if (agentId != null) {
                log.info("排队分配成功 sessionId={} agentId={}", msg.getSessionId(), agentId);
            } else {
                log.info("暂无可用客服，sessionId={} 继续等待", msg.getSessionId());
            }
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("排队分配失败", e);
            try { channel.basicAck(deliveryTag, false); } catch (Exception ignored) {}
        }
    }
}
