package com.xs.sheepaimall.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 客服日用量聚合：用户行(user_id+stat_date)与商家行(merchant_id+stat_date)各一条，异步对账持久化（Redis 实时值为准） */
@Data
@TableName("cs_daily_usage")
@Schema(description = "客服日用量聚合")
public class CsDailyUsage {

    @Schema(description = "记录ID")
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @Schema(description = "用户ID，商家行为null")
    private Long userId;

    @Schema(description = "商家ID，用户行为null（平台行两者仅其一）")
    private Long merchantId;

    @Schema(description = "统计日期")
    private LocalDate statDate;

    @Schema(description = "当日请求数")
    private Integer requestCount;

    @Schema(description = "当日消耗Token数")
    private Long tokenUsed;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
