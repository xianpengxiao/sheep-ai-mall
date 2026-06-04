package com.xs.sheepaimall.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 商品评价 */
@Data
@TableName("product_review")
public class ProductReview {

    @Schema(description = "评价ID")
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @Schema(description = "商品SPU ID")
    private Long spuId;

    @Schema(description = "商品SKU ID")
    private Long skuId;

    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "订单明细ID")
    private Long orderItemId;

    @Schema(description = "评价用户ID")
    private Long userId;

    @Schema(description = "评分 1-5")
    private Integer rating;

    @Schema(description = "评价内容")
    private String content;

    @Schema(description = "评价图片列表JSON")
    private String imageList;

    @Schema(description = "状态 0隐藏 1显示")
    private Integer status;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
