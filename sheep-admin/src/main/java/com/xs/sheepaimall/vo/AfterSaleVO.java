package com.xs.sheepaimall.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/** 售后单返回（含订单商品明细） */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "售后单（含商品明细）")
public class AfterSaleVO extends com.xs.sheepaimall.entity.AfterSale {

    @Schema(description = "订单商品明细")
    private List<OrderItemVO> items;
}
