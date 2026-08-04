package com.xs.sheepaimall.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 购物车 */
@Data
@TableName("cart")
public class Cart {

    @Schema(description = "购物车ID")
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @Schema(description = "会员ID")
    private Long userId;

    @Schema(description = "SPU ID")
    private Long spuId;

    @Schema(description = "SKU ID")
    private Long skuId;

    @Schema(description = "数量")
    private Integer quantity;

    @Schema(description = "是否选中：1=是 0=否")
    private Integer selected;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
