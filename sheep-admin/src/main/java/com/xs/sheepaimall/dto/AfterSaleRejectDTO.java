package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 售后拒绝请求 */
@Data
@Schema(description = "售后拒绝请求")
public class AfterSaleRejectDTO {

    @Schema(description = "拒绝原因", example = "商品已使用，影响二次销售")
    @NotBlank(message = "拒绝原因不能为空")
    private String rejectReason;
}
