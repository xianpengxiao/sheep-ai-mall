package com.xs.sheepaimall.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/** 店铺DSR评分 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "店铺DSR评分")
public class MerchantDsrVO {

    @Schema(description = "描述相符评分")
    private BigDecimal describeScore;

    @Schema(description = "服务态度评分")
    private BigDecimal serviceScore;

    @Schema(description = "物流服务评分")
    private BigDecimal logisticsScore;

    @Schema(description = "近90天有效评价数")
    private Integer totalCount;

    @Schema(description = "本月评价数")
    private Integer monthCount;

    @Schema(description = "描述高分占比(小数)")
    private Double describeHighRate;

    @Schema(description = "服务高分占比(小数)")
    private Double serviceHighRate;

    @Schema(description = "物流高分占比(小数)")
    private Double logisticsHighRate;

    /** 商家后台DSR趋势 */
    @Schema(description = "近30天DSR趋势")
    private List<DsrTrendItem> trend;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "DSR趋势项")
    public static class DsrTrendItem {
        @Schema(description = "统计日期")
        private String statDate;

        @Schema(description = "描述相符评分")
        private BigDecimal describeScore;

        @Schema(description = "服务态度评分")
        private BigDecimal serviceScore;

        @Schema(description = "物流服务评分")
        private BigDecimal logisticsScore;

        @Schema(description = "近90天有效评价数")
        private Integer totalCount;

        @Schema(description = "本月评价数")
        private Integer monthCount;
    }
}
