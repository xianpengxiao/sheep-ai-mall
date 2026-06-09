package com.xs.sheepaimall.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 系统用户
 */
@Data
@TableName("sys_user")
public class SysUser {

    @Schema(description = "用户ID")
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @Schema(description = "登录账号")
    private String username;

    @Schema(description = "加密密码(BCrypt)")
    private String password;

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

    @Schema(description = "资料是否完善 0未完善 1已完善")
    private Integer isPerfect;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "头像URL")
    private String avatar;

    @Schema(description = "微信openid（用于微信支付）")
    private String openid;

    @Schema(description = "状态：0=禁用 1=正常 2=锁定")
    private Integer status;

    @Schema(description = "最后登录时间")
    private LocalDateTime lastLogin;

    @Schema(description = "最后登录IP")
    private String loginIp;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "逻辑删除 0未删除 1已删除")
    @TableLogic
    private Integer deleted;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
