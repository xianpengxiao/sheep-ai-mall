package com.xs.sheepaimall.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户信息响应（管理员视角，含角色信息）
 */
@Data
@Builder
@Schema(description = "用户信息响应")
public class UserVO {

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "登录账号")
    private String username;

    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "头像URL")
    private String avatar;

    @Schema(description = "状态：0=禁用 1=正常 2=锁定")
    private Integer status;

    @Schema(description = "最后登录时间")
    private LocalDateTime lastLogin;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "性别 0未知 1男 2女")
    private Integer gender;

    @Schema(description = "生日")
    private LocalDate birthday;

    @Schema(description = "个性签名")
    private String signature;

    @Schema(description = "身份证号")
    private String idCard;

    @Schema(description = "资料是否完善 0未完善 1已完善")
    private Integer isPerfect;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "角色编码列表")
    private List<String> roles;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
