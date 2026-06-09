package com.xs.sheepaimall.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * 用户资料响应（不含密码等敏感字段）
 */
@Data
@Builder
@Schema(description = "用户资料响应")
public class UserProfileVO {

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "登录账号")
    private String username;

    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "性别 0未知 1男 2女")
    private Integer gender;

    @Schema(description = "生日")
    private LocalDate birthday;

    @Schema(description = "个性签名")
    private String signature;

    @Schema(description = "头像URL")
    private String avatar;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "资料是否完善 0未完善 1已完善")
    private Integer isPerfect;

    @Schema(description = "创建时间")
    private String createTime;
}
