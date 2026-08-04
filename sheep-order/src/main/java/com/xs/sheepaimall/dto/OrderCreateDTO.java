package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 创建订单请求 */
@Data
@Schema(description = "创建订单请求")
public class OrderCreateDTO {

    @Schema(description = "收货人姓名")
    @NotBlank(message = "收货人姓名不能为空")
    private String receiverName;

    @Schema(description = "收货人电话")
    @NotBlank(message = "收货人电话不能为空")
    private String receiverPhone;

    @Schema(description = "收货地址")
    @NotBlank(message = "收货地址不能为空")
    private String receiverAddress;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "订单明细列表")
    @NotEmpty(message = "订单明细不能为空")
    @Valid
    private List<OrderItemDTO> items;
}
