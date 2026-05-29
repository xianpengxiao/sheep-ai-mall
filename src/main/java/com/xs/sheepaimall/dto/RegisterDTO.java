package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册/创建账号入参
 */
@Data
@Schema(description = "注册请求参数")
public class RegisterDTO {

    @NotBlank(message = "账号不能为空")
    @Size(min = 3, max = 64, message = "账号长度需在 3-64 之间")
    @Schema(description = "登录账号", example = "zhangsan")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需在 6-32 之间")
    @Schema(description = "登录密码", example = "123456")
    private String password;

    @Schema(description = "真实姓名", example = "张三")
    private String realName;

    @Schema(description = "手机号", example = "13800138000")
    private String phone;

    @Schema(description = "邮箱", example = "zhangsan@example.com")
    private String email;
}
