package com.xs.sheepaimall.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("merchant_dsr")
@Schema(description = "商家DSR评分汇总")
public class MerchantDsr {

    @Schema(description = "ID")
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @Schema(description = "商家ID")
    private Long merchantId;

    @Schema(description = "描述相符评分(近90天)")
    private BigDecimal describeScore;

    @Schema(description = "服务态度评分(近90天)")
    private BigDecimal serviceScore;

    @Schema(description = "物流服务评分(近90天)")
    private BigDecimal logisticsScore;

    @Schema(description = "近90天有效评价数")
    private Integer totalCount;

    @Schema(description = "本月评价数")
    private Integer monthCount;

    @Schema(description = "描述高分(4-5)数")
    private Integer describeHigh;

    @Schema(description = "服务高分(4-5)数")
    private Integer serviceHigh;

    @Schema(description = "物流高分(4-5)数")
    private Integer logisticsHigh;

    @Schema(description = "统计日期")
    private LocalDate statDate;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
