package com.xs.sheepaimall.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 订单明细 */
@Data
@TableName("order_item")
public class OrderItem {

    @Schema(description = "明细ID")
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "SPU ID")
    private Long spuId;

    @Schema(description = "SKU ID")
    private Long skuId;

    @Schema(description = "SKU名称快照")
    private String skuName;

    @Schema(description = "单价快照")
    private BigDecimal price;

    @Schema(description = "数量")
    private Integer quantity;

    @Schema(description = "小计")
    private BigDecimal totalPrice;

    @Schema(description = "商品图片快照")
    private String image;

    @Schema(description = "逻辑删除：0=否 1=是")
    @TableLogic
    private Integer deleted;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
