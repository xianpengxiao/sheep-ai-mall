package com.xs.sheepaimall.consumer;

import com.rabbitmq.client.Channel;
import com.xs.sheepaimall.common.RabbitMQConstants;
import com.xs.sheepaimall.core.CsPollHolder;
import com.xs.sheepaimall.dto.CsMQMessage;
import com.xs.sheepaimall.vo.CsMessageVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.List;

/** 实时推送：有新消息时唤醒长轮询的 DeferredResult */
@Slf4j
@Component
public class MessagePushConsumer {

    @Autowired
    private CsPollHolder csPollHolder;

    @RabbitListener(queues = RabbitMQConstants.CS_PUSH_QUEUE, containerFactory = "csListenerContainerFactory")
    public void handlePush(CsMQMessage msg, Channel channel,
                            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            // 构建 VO
            CsMessageVO vo = new CsMessageVO();
            vo.setId(msg.getBizMessageId());
            vo.setSessionId(msg.getSessionId());
            vo.setSenderId(msg.getSenderId());
            vo.setSenderRole(msg.getSenderRole());
            vo.setMsgType(msg.getMsgType());
            vo.setContent(msg.getContent());
            vo.setExtra(msg.getExtra());
            vo.setCreateTime(msg.getCreateTime());

            // 唤醒该会话的轮询
            csPollHolder.wakeUp(msg.getSessionId(), List.of(vo));

            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("消息推送失败", e);
            try { channel.basicAck(deliveryTag, false); } catch (Exception ignored) {}
        }
    }
}
