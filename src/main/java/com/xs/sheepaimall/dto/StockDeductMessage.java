package com.xs.sheepaimall.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 库存扣减消息体 —— 订单创建后投递到 RabbitMQ，由消费者异步处理
 */
@Data
public class StockDeductMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 消息唯一ID（幂等标识） */
    private String messageId;

    /** 订单ID */
    private Long orderId;

    /** 扣减明细 */
    private List<StockDeductItem> items;

    /** 消息创建时间 */
    private LocalDateTime createTime;

    /**
     * 单条库存扣减明细
     */
    @Data
    public static class StockDeductItem implements Serializable {

        private static final long serialVersionUID = 1L;

        /** SPU ID（用于更新销量） */
        private Long spuId;

        /** SKU ID（用于扣减库存） */
        private Long skuId;

        /** 扣减数量 */
        private Integer quantity;
    }
}
