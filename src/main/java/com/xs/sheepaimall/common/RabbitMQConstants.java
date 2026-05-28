package com.xs.sheepaimall.common;

/**
 * RabbitMQ 常量定义 —— 交换机、队列、路由键及重试/幂等参数
 */
public final class RabbitMQConstants {

    private RabbitMQConstants() {}

    // ==================== 交换机 ====================

    /** 库存操作 Topic 交换机 */
    public static final String STOCK_EXCHANGE = "sheep.mall.stock.exchange";

    /** 死信交换机 */
    public static final String STOCK_DLX_EXCHANGE = "sheep.mall.stock.dlx.exchange";

    // ==================== 队列 ====================

    /** 库存锁定队列（下单时锁定库存） */
    public static final String STOCK_LOCK_QUEUE = "sheep.mall.stock.lock.queue";

    /** 库存锁定死信队列 */
    public static final String STOCK_LOCK_DLQ = "sheep.mall.stock.lock.dlq";

    /** 库存释放队列（取消订单/支付超时释放锁定） */
    public static final String STOCK_RELEASE_QUEUE = "sheep.mall.stock.release.queue";

    /** 库存释放死信队列 */
    public static final String STOCK_RELEASE_DLQ = "sheep.mall.stock.release.dlq";

    // ==================== 路由键 ====================

    /** 库存锁定路由键 */
    public static final String STOCK_LOCK_ROUTING_KEY = "stock.lock";

    /** 库存释放路由键 */
    public static final String STOCK_RELEASE_ROUTING_KEY = "stock.release";

    /** 库存锁定死信路由键 */
    public static final String STOCK_LOCK_DLQ_ROUTING_KEY = "stock.lock.dlq";

    /** 库存释放死信路由键 */
    public static final String STOCK_RELEASE_DLQ_ROUTING_KEY = "stock.release.dlq";

    // ==================== 消息头 ====================

    /** 重试次数消息头 */
    public static final String RETRY_COUNT_HEADER = "x-retry-count";

    // ==================== 重试/幂等参数 ====================

    /** 最大重试次数 */
    public static final int MAX_RETRY_COUNT = 3;

    /** 库存锁定幂等 Key 前缀 */
    public static final String STOCK_LOCK_IDEMPOTENT_PREFIX = "stock:lock:msg:";

    /** 库存释放幂等 Key 前缀 */
    public static final String STOCK_RELEASE_IDEMPOTENT_PREFIX = "stock:release:msg:";

    /** 幂等锁 TTL（秒），24小时 */
    public static final long IDEMPOTENT_TTL_SECONDS = 86400;

    // ==================== 库存状态追踪（Redis） ====================

    /** 库存锁定状态 Key 前缀：stock:locked:{orderId} */
    public static final String STOCK_LOCKED_STATUS_PREFIX = "stock:locked:";

    /** 库存锁定状态 TTL（秒），30分钟——超过此时间未支付则自动视为过期 */
    public static final long STOCK_LOCKED_TTL_SECONDS = 1800;
}
