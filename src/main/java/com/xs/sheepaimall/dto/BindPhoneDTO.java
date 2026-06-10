package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 绑定手机号请求
 */
@Data
@Schema(description = "绑定手机号请求")
public class BindPhoneDTO {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "手机号")
    private String phone;

    @NotBlank(message = "验证码不能为空")
    @Schema(description = "新手机号验证码")
    private String code;

    @Schema(description = "原手机号验证码（已绑定手机号时必填）")
    private String oldCode;
}
