package com.xs.sheepaimall.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 商品评价返回 */
@Data
@Schema(description = "商品评价")
public class ReviewVO {

    @Schema(description = "评价ID")
    private Long id;

    @Schema(description = "商品SPU ID")
    private Long spuId;

    @Schema(description = "商品SPU名称")
    private String spuName;

    @Schema(description = "商品SKU名称")
    private String skuName;

    @Schema(description = "评价用户ID")
    private Long userId;

    @Schema(description = "评价用户昵称")
    private String username;

    @Schema(description = "用户头像")
    private String avatar;

    @Schema(description = "评分 1-5")
    private Integer rating;

    @Schema(description = "描述相符评分 1-5")
    private Integer describeScore;

    @Schema(description = "服务态度评分 1-5")
    private Integer serviceScore;

    @Schema(description = "物流服务评分 1-5")
    private Integer logisticsScore;

    @Schema(description = "评价内容")
    private String content;

    @Schema(description = "评价图片URL列表")
    private List<String> imageList;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "订单明细ID")
    private Long orderItemId;

    @Schema(description = "显示状态 0隐藏 1显示")
    private Integer status;

    @Schema(description = "评价状态 0待评 1已评 2已过期")
    private Integer reviewStatus;
}
