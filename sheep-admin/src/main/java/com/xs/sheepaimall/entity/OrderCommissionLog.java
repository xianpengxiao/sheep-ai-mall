package com.xs.sheepaimall.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 订单分佣记录 */
@Data
@TableName("order_commission_log")
public class OrderCommissionLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long orderId;

    private Long orderItemId;

    private String orderNo;

    private Long spuId;

    private Long categoryId;

    private Long merchantId;

    /** 商品实付金额 */
    private BigDecimal totalPrice;

    /** 佣金比例(%) */
    private BigDecimal commissionRate;

    /** 平台佣金 */
    private BigDecimal commissionAmount;

    /** 商家到手金额 */
    private BigDecimal merchantIncome;

    /** 0待结算 1已结算 2已退款 */
    private Integer status;

    private LocalDateTime settleTime;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
