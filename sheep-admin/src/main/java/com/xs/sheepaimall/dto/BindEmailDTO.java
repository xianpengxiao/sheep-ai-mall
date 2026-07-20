package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 绑定邮箱请求
 */
@Data
@Schema(description = "绑定邮箱请求")
public class BindEmailDTO {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Schema(description = "邮箱")
    private String email;

    @NotBlank(message = "验证码不能为空")
    @Schema(description = "新邮箱验证码")
    private String code;

    @Schema(description = "原邮箱验证码（已绑定邮箱时必填）")
    private String oldCode;
}
