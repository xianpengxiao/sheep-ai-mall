package com.xs.sheepaimall.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 客服商家套餐：月度Token额度与默认日限（额度体系约束链的套餐层） */
@Data
@TableName("cs_plan")
@Schema(description = "客服商家套餐")
public class CsPlan {

    @Schema(description = "套餐ID")
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @Schema(description = "套餐名称")
    private String name;

    @Schema(description = "月度Token额度")
    private Long monthlyTokenQuota;

    @Schema(description = "默认日请求上限")
    private Integer dailyRequestLimit;

    @Schema(description = "默认日Token上限")
    private Integer dailyTokenLimit;

    @Schema(description = "价格（元）")
    private BigDecimal price;

    @Schema(description = "状态：0=停用 1=启用")
    private Integer status;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
