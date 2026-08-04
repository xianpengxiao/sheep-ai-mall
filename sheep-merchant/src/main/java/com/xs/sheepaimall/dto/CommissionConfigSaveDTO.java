package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 新增/编辑类目佣金规则 */
@Data
@Schema(description = "类目佣金规则")
public class CommissionConfigSaveDTO {

    @NotNull(message = "分类ID不能为空")
    @Schema(description = "商品分类ID")
    private Long categoryId;

    @NotNull(message = "佣金比例不能为空")
    @DecimalMin(value = "0.00", message = "佣金比例不能小于0")
    @DecimalMax(value = "100.00", message = "佣金比例不能超过100")
    @Schema(description = "佣金比例(%)")
    private BigDecimal commissionRate;

    @Schema(description = "生效日期")
    private LocalDate effectiveDate;

    @Schema(description = "失效日期（空=长期）")
    private LocalDate expireDate;

    @Schema(description = "状态 0禁用 1启用")
    private Integer status;

    @Schema(description = "备注")
    private String remark;
}
