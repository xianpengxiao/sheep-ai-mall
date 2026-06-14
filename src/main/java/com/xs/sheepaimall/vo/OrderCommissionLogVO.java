package com.xs.sheepaimall.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 订单分佣记录VO */
@Data
@Schema(description = "订单分佣记录")
public class OrderCommissionLogVO {

    @Schema(description = "记录ID")
    private Long id;

    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "SPU ID")
    private Long spuId;

    @Schema(description = "商品名称")
    private String spuName;

    @Schema(description = "分类ID")
    private Long categoryId;

    @Schema(description = "分类名称")
    private String categoryName;

    @Schema(description = "商家ID")
    private Long merchantId;

    @Schema(description = "店铺名称")
    private String shopName;

    @Schema(description = "商品实付金额")
    private BigDecimal totalPrice;

    @Schema(description = "佣金比例(%)")
    private BigDecimal commissionRate;

    @Schema(description = "平台佣金")
    private BigDecimal commissionAmount;

    @Schema(description = "商家到手金额")
    private BigDecimal merchantIncome;

    @Schema(description = "状态 0待结算 1已结算 2已退款")
    private Integer status;

    @Schema(description = "结算时间")
    private LocalDateTime settleTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
