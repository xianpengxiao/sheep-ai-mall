package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** 添加/修改收货地址请求 */
@Data
@Schema(description = "添加/修改收货地址请求")
public class AddressSaveDTO {

    @Schema(description = "地址ID（修改时传，新增时不传）")
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    @NotBlank(message = "收货人姓名不能为空")
    @Schema(description = "收货人姓名")
    private String receiverName;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "收货人手机号")
    private String receiverPhone;

    @NotBlank(message = "省份不能为空")
    @Schema(description = "省份")
    private String province;

    @NotBlank(message = "城市不能为空")
    @Schema(description = "城市")
    private String city;

    @NotBlank(message = "区县不能为空")
    @Schema(description = "区县")
    private String district;

    @NotBlank(message = "详细地址不能为空")
    @Schema(description = "详细地址")
    private String detailAddress;

    @Schema(description = "邮政编码")
    private String zipCode;

    @Schema(description = "是否默认地址 0=否 1=是")
    private Integer isDefault;
}
