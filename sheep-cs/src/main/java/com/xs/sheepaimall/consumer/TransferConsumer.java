package com.xs.sheepaimall.consumer;

import com.rabbitmq.client.Channel;
import com.xs.sheepaimall.common.RabbitMQConstants;
import com.xs.sheepaimall.dto.CsMQMessage;
import com.xs.sheepaimall.service.CsQueueService;
import com.xs.sheepaimall.service.CsSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/** AI转人工处理 */
@Slf4j
@Component
public class TransferConsumer {

    @Autowired
    private CsSessionService csSessionService;

    @Autowired
    private CsQueueService csQueueService;

    @RabbitListener(queues = RabbitMQConstants.CS_TRANSFER_QUEUE, containerFactory = "csListenerContainerFactory")
    public void handleTransfer(CsMQMessage msg, Channel channel,
                                @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            // 1. 尝试分配客服
            Long agentId = csQueueService.assignAgent(msg.getSessionId());
            if (agentId != null) {
                csSessionService.transferToHuman(msg.getSessionId(), agentId);
                log.info("转人工成功 sessionId={} agentId={}", msg.getSessionId(), agentId);
            } else {
                // 2. 无可用客服，进入排队
                csSessionService.transferToHuman(msg.getSessionId(), null);
                csQueueService.enqueue(msg.getSessionId(), msg.getSenderId());
                log.info("转人工排队 sessionId={}", msg.getSessionId());
            }
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("转人工处理失败", e);
            try { channel.basicAck(deliveryTag, false); } catch (Exception ignored) {}
        }
    }
}
