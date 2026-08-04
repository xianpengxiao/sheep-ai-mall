package com.xs.sheepaimall.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 店铺订单返回（商家端） */
@Data
@Schema(description = "店铺订单")
public class MerchantOrderVO {

    @Schema(description = "订单ID")
    private Long id;

    @Schema(description = "订单编号")
    private String orderNo;

    @Schema(description = "商品总金额")
    private BigDecimal totalAmount;

    @Schema(description = "实付金额")
    private BigDecimal payAmount;

    @Schema(description = "订单状态 0待支付 1已支付 2已发货 3已完成 4已取消")
    private Integer status;

    @Schema(description = "状态文本")
    private String statusText;

    @Schema(description = "收货人姓名")
    private String receiverName;

    @Schema(description = "收货人电话")
    private String receiverPhone;

    @Schema(description = "收货地址")
    private String receiverAddress;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "支付时间")
    private LocalDateTime payTime;

    @Schema(description = "发货时间")
    private LocalDateTime deliveryTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "订单明细（属于该店铺的商品）")
    private java.util.List<OrderItemVO> items;
}
