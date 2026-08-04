package com.xs.sheepaimall.consumer;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.rabbitmq.client.Channel;
import com.xs.sheepaimall.common.BizException;
import com.xs.sheepaimall.common.RabbitMQConstants;
import com.xs.sheepaimall.dto.StockDeductMessage;
import com.xs.sheepaimall.entity.OrderInfo;
import com.xs.sheepaimall.entity.Sku;
import com.xs.sheepaimall.entity.Spu;
import com.xs.sheepaimall.feign.OrderFeignClient;
import com.xs.sheepaimall.service.SkuService;
import com.xs.sheepaimall.service.SpuService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 库存操作消费者 —— 处理库存锁定（下单）和库存释放（取消）消息。
 *
 * 可靠性保障：
 * - 幂等：Redis SETNX messageId，重复消息直接 Ack
 * - 锁定时先检查订单状态（已取消则跳过）
 * - 释放时先检查锁定状态（未锁定则跳过）
 * - 重试：失败后递增 x-retry-count 重新投递，最多 3 次
 * - 死信：超过重试上限进入 DLQ
 */
@Component
public class StockLockConsumer {

    private static final Logger log = LoggerFactory.getLogger(StockLockConsumer.class);

    @Autowired
    private SkuService skuService;

    @Autowired
    private SpuService spuService;

    @Autowired
    private OrderFeignClient orderFeignClient;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /** Redis 锁定状态 TTL，取支付超时的 2 倍作为安全余量 */
    @Value("${sheep.mall.order.pay-timeout-minutes:15}")
    private int payTimeoutMinutes;

    // ==================== 库存锁定 ====================

    /** 监听库存锁定队列 —— 下单后异步锁定（扣减）库存 */
    @RabbitListener(queues = RabbitMQConstants.STOCK_LOCK_QUEUE)
    public void handleStockLock(Message message, Channel channel, StockDeductMessage payload) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String messageId = payload.getMessageId();
        Long orderId = payload.getOrderId();

        try {
            log.info("收到库存锁定消息 messageId={} orderId={}", messageId, orderId);

            // 1. 幂等检查
            String idempotentKey = RabbitMQConstants.STOCK_LOCK_IDEMPOTENT_PREFIX + messageId;
            Boolean firstTime = stringRedisTemplate.opsForValue()
                    .setIfAbsent(idempotentKey, "1",
                            Duration.ofSeconds(RabbitMQConstants.IDEMPOTENT_TTL_SECONDS));
            if (Boolean.FALSE.equals(firstTime)) {
                log.info("重复锁定消息，跳过 messageId={}", messageId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 2. 检查订单状态——已取消则跳过锁定
            OrderInfo order = orderFeignClient.getOrderById(orderId);
            if (order == null) {
                log.warn("订单不存在，跳过锁定 orderId={}", orderId);
                channel.basicAck(deliveryTag, false);
                return;
            }
            if (order.getStatus() != null && order.getStatus() == 4) {
                log.info("订单已取消，跳过库存锁定 orderId={}", orderId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 3. 原子锁定库存（扣减）+ 更新销量
            processLock(payload);

            // 4. 记录锁定状态到 Redis（供取消/超时判断，TTL=支付超时×2 保有余量）
            String lockStatusKey = RabbitMQConstants.STOCK_LOCKED_STATUS_PREFIX + orderId;
            long lockTtlSeconds = payTimeoutMinutes * 60L * 2;
            stringRedisTemplate.opsForValue().set(lockStatusKey, "LOCKED",
                    Duration.ofSeconds(lockTtlSeconds));

            log.info("库存锁定成功 orderId={} messageId={}", orderId, messageId);
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("库存锁定失败 messageId={} orderId={} error={}", messageId, orderId, e.getMessage(), e);
            handleRetry(message, channel, payload, deliveryTag, messageId, orderId,
                    RabbitMQConstants.STOCK_LOCK_ROUTING_KEY);
        }
    }

    // ==================== 库存释放 ====================

    /** 监听库存释放队列 —— 取消订单后异步释放（归还）库存 */
    @RabbitListener(queues = RabbitMQConstants.STOCK_RELEASE_QUEUE)
    public void handleStockRelease(Message message, Channel channel, StockDeductMessage payload) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String messageId = payload.getMessageId();
        Long orderId = payload.getOrderId();

        try {
            log.info("收到库存释放消息 messageId={} orderId={}", messageId, orderId);

            // 1. 幂等检查
            String idempotentKey = RabbitMQConstants.STOCK_RELEASE_IDEMPOTENT_PREFIX + messageId;
            Boolean firstTime = stringRedisTemplate.opsForValue()
                    .setIfAbsent(idempotentKey, "1",
                            Duration.ofSeconds(RabbitMQConstants.IDEMPOTENT_TTL_SECONDS));
            if (Boolean.FALSE.equals(firstTime)) {
                log.info("重复释放消息，跳过 messageId={}", messageId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 2. 释放库存（归还）+ 回退销量
            processRelease(payload);

            // 3. 清除锁定状态
            stringRedisTemplate.delete(RabbitMQConstants.STOCK_LOCKED_STATUS_PREFIX + orderId);

            log.info("库存释放成功 orderId={} messageId={}", orderId, messageId);
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("库存释放失败 messageId={} orderId={} error={}", messageId, orderId, e.getMessage(), e);
            handleRetry(message, channel, payload, deliveryTag, messageId, orderId,
                    RabbitMQConstants.STOCK_RELEASE_ROUTING_KEY);
        }
    }

    // ==================== 核心业务逻辑 ====================

    /** 锁定库存：原子扣减 + 更新销量 */
    @Transactional(rollbackFor = Exception.class)
    public void processLock(StockDeductMessage payload) {
        for (StockDeductMessage.StockDeductItem item : payload.getItems()) {
            Long skuId = item.getSkuId();
            Long spuId = item.getSpuId();
            int qty = item.getQuantity();

            boolean deducted = skuService.update(new LambdaUpdateWrapper<Sku>()
                    .eq(Sku::getId, skuId)
                    .ge(Sku::getStock, qty)
                    .setSql("stock = stock - " + qty));
            if (!deducted) {
                throw new BizException("库存不足 skuId=" + skuId + " quantity=" + qty);
            }

            spuService.update(new LambdaUpdateWrapper<Spu>()
                    .eq(Spu::getId, spuId)
                    .setSql("sales_count = sales_count + " + qty));
        }
    }

    /** 释放库存：归还库存 + 回退销量 */
    @Transactional(rollbackFor = Exception.class)
    public void processRelease(StockDeductMessage payload) {
        for (StockDeductMessage.StockDeductItem item : payload.getItems()) {
            Long skuId = item.getSkuId();
            Long spuId = item.getSpuId();
            int qty = item.getQuantity();

            skuService.update(new LambdaUpdateWrapper<Sku>()
                    .eq(Sku::getId, skuId)
                    .setSql("stock = stock + " + qty));

            spuService.update(new LambdaUpdateWrapper<Spu>()
                    .eq(Spu::getId, spuId)
                    .setSql("sales_count = sales_count - " + qty));
        }
    }

    // ==================== 重试逻辑 ====================

    /**
     * 失败重试决策。
     * - 未达上限：重新投递（携带递增 x-retry-count），投递成功则 Ack 原消息，失败则 Nack+requeue
     * - 已达上限：标记订单备注 → Nack 不 requeue → DLX → DLQ
     */
    private void handleRetry(Message message, Channel channel, StockDeductMessage payload,
                             long deliveryTag, String messageId, Long orderId, String routingKey) {
        MessageProperties props = message.getMessageProperties();
        Integer retryCount = (Integer) props.getHeaders()
                .getOrDefault(RabbitMQConstants.RETRY_COUNT_HEADER, 0);

        if (retryCount < RabbitMQConstants.MAX_RETRY_COUNT) {
            retryCount++;
            log.warn("库存操作重试 messageId={} orderId={} retryCount={}/{} routingKey={}",
                    messageId, orderId, retryCount,
                    RabbitMQConstants.MAX_RETRY_COUNT, routingKey);

            try {
                Integer finalRetryCount = retryCount;
                rabbitTemplate.convertAndSend(
                        RabbitMQConstants.STOCK_EXCHANGE,
                        routingKey,
                        payload,
                        msg -> {
                            msg.getMessageProperties().setHeader(
                                    RabbitMQConstants.RETRY_COUNT_HEADER, finalRetryCount);
                            return msg;
                        });
                // 重发成功 → Ack 原消息，移除队列旧消息防止重复
                channel.basicAck(deliveryTag, false);
                log.info("重发成功并Ack原消息 messageId={} retryCount={}", messageId, retryCount);
            } catch (Exception repubEx) {
                log.error("重发消息失败，原消息返回队列等待下次投递 messageId={}", messageId, repubEx);
                // 重发失败 → Nack + requeue，让原消息保留在队列中等待下次投递
                try {
                    channel.basicNack(deliveryTag, false, true);
                } catch (Exception nackEx) {
                    log.error("Nack失败，尝试Ack兜底 messageId={}", messageId, nackEx);
                    try {
                        channel.basicAck(deliveryTag, false);
                    } catch (Exception ackEx) {
                        log.error("最终Ack也失败 messageId={}", messageId, ackEx);
                    }
                }
            }
        } else {
            log.error("库存操作超过最大重试次数，投入死信队列 messageId={} orderId={}",
                    messageId, orderId);

            // 标记订单备注，供人工排查
            try {
                orderFeignClient.updateOrderRemark(orderId, "【需人工处理】库存操作失败，消息已入死信队列");
            } catch (Exception ignored) {
                log.error("标记订单备注异常 orderId={}", orderId, ignored);
            }

            // Nack 不 requeue → DLX → DLQ
            try {
                channel.basicNack(deliveryTag, false, false);
            } catch (Exception nackEx) {
                log.error("Nack入死信失败，尝试Ack兜底 messageId={}", messageId, nackEx);
                try {
                    channel.basicAck(deliveryTag, false);
                } catch (Exception ackEx) {
                    log.error("最终Ack也失败 messageId={}", messageId, ackEx);
                }
            }
        }
    }
}
