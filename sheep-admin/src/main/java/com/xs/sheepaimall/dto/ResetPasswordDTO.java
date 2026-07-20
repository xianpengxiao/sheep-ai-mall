package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 找回密码 — 免旧密码重置密码 */
@Data
@Schema(description = "找回密码请求参数")
public class ResetPasswordDTO {

    @Schema(description = "手机号（与邮箱二选一）")
    private String phone;

    @Schema(description = "邮箱（与手机号二选一）")
    private String email;

    @NotBlank(message = "验证码不能为空")
    @Schema(description = "验证码")
    private String code;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需在 6-32 之间")
    @Schema(description = "新密码", example = "654321")
    private String newPassword;
}
