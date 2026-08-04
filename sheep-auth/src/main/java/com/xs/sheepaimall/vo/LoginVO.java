package com.xs.sheepaimall.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 登录响应
 */
@Data
@Builder
@Schema(description = "登录响应")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginVO {

    @Schema(description = "JWT 访问令牌")
    private String accessToken;

    @Schema(description = "令牌类型，固定 Bearer")
    private String tokenType;

    @Schema(description = "过期时间（秒）")
    private Long expiresIn;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "登录账号")
    private String username;

    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "头像URL")
    private String avatar;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "性别 0未知 1男 2女")
    private Integer gender;

    @Schema(description = "生日")
    private LocalDate birthday;

    @Schema(description = "个性签名")
    private String signature;

    @Schema(description = "资料是否完善 0未完善 1已完善")
    private Integer isPerfect;

    @Schema(description = "状态 0禁用 1正常 2锁定")
    private Integer status;

    @Schema(description = "最后登录时间")
    private LocalDateTime lastLogin;

    @Schema(description = "角色编码列表")
    private List<String> roles;

    @Schema(description = "权限标识列表")
    private List<String> permissions;
}
