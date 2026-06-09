package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 用户资料修改请求
 */
@Data
@Schema(description = "用户资料修改请求")
public class UserProfileUpdateDTO {

    @Schema(description = "昵称（2-20个字符）")
    @Size(min = 2, max = 20, message = "昵称长度2-20个字符")
    private String nickname;

    @Schema(description = "性别 0未知 1男 2女")
    private Integer gender;

    @Schema(description = "生日 yyyy-MM-dd")
    private LocalDate birthday;

    @Schema(description = "个性签名（最多200字）")
    @Size(max = 200, message = "个性签名最多200字")
    private String signature;

    @Schema(description = "头像URL（可复用上传接口获取后填入）")
    private String avatar;
}
