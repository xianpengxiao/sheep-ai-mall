package com.xs.sheepaimall.controller;

import com.xs.sheepaimall.common.R;
import com.xs.sheepaimall.dto.*;
import com.xs.sheepaimall.entity.SysUser;
import com.xs.sheepaimall.security.RequirePermission;
import com.xs.sheepaimall.security.UserContext;
import com.xs.sheepaimall.service.SysUserService;
import com.xs.sheepaimall.util.DesensitizeUtil;
import com.xs.sheepaimall.util.OssUtil;
import com.xs.sheepaimall.vo.LoginVO;
import com.xs.sheepaimall.vo.SecurityProfileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 认证接口：登录、退出登录、注册、修改密码/头像、获取当前用户信息
 */
@Tag(name = "认证", description = "登录、退出登录、注册、个人信息管理")
@Validated
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Resource
    private SysUserService sysUserService;

    @Resource
    private OssUtil ossUtil;

    @Operation(summary = "账号密码登录")
    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        LoginVO vo = sysUserService.login(dto);
        return R.ok(vo);
    }

    @Operation(summary = "退出登录", description = "使当前 JWT Token 失效（加入 Redis 黑名单）")
    @PostMapping("/logout")
    public R<Void> logout() {
        String token = UserContext.getToken();
        sysUserService.logout(token);
        return R.ok();
    }

    @Operation(summary = "短信验证码登录", description = "手机号需先通过验证码验证，验证通过后直接登录返回 JWT Token")
    @PostMapping("/sms-login")
    public R<LoginVO> smsLogin(@Valid @RequestBody VerifyCodeDTO dto) {
        return R.ok(sysUserService.smsLogin(dto.getPhone(), dto.getCode()));
    }

    @Operation(summary = "发送短信验证码（登录用）", description = "校验手机号已注册后发送验证码")
    @PostMapping("/send-login-code")
    public R<String> sendLoginCode(@RequestParam String phone) {
        sysUserService.sendLoginCode(phone);
        return R.ok("验证码已发送");
    }

    @Operation(summary = "检查手机号是否已注册")
    @GetMapping("/check-phone")
    public R<Boolean> checkPhone(@RequestParam String phone) {
        return R.ok(sysUserService.checkPhoneExists(phone));
    }

    @Operation(summary = "发送短信验证码", description = "校验手机号格式和唯一性，60秒内不可重复发送")
    @PostMapping("/send-code")
    public R<String> sendCode(@RequestParam String phone) {
        sysUserService.sendVerifyCode(phone);
        return R.ok("验证码已发送");
    }

    @Operation(summary = "校验短信验证码", description = "验证码正确后标记手机号已验证（有效期10分钟），用于注册时校验")
    @PostMapping("/verify-code")
    public R<Boolean> verifyCode(@Valid @RequestBody VerifyCodeDTO dto) {
        return R.ok(sysUserService.verifyCode(dto.getPhone(), dto.getCode()));
    }

    @Operation(summary = "注册新账号", description = "创建系统用户，自动分配默认角色（只读），手机号需先通过验证码验证")
    @PostMapping("/register")
    public R<Long> register(@Valid @RequestBody RegisterDTO dto) {
        SysUser user = sysUserService.register(dto);
        return R.ok("注册成功", user.getId());
    }

    @Operation(summary = "修改密码", description = "校验旧密码后更新为新密码")
    @PutMapping("/password")
    public R<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        sysUserService.changePassword(UserContext.getUserId(), dto.getOldPassword(), dto.getNewPassword());
        return R.ok();
    }

    @Operation(summary = "修改头像", description = "接收 Base64 图片，上传后自动更新用户头像字段，并删除旧头像")
    @PutMapping("/avatar")
    public R<Void> updateAvatar(
            @Parameter(description = "Base64 图片数据（支持 data:image/xxx;base64, 前缀格式）") @RequestParam String avatarUrl) {
        Long userId = UserContext.getUserId();
        // 1. 查询旧头像 URL
        SysUser user = sysUserService.getById(userId);
        if (user == null) {
            return R.fail("用户不存在");
        }
        String oldAvatar = user.getAvatar();
        // 2. 上传新头像
        String newUrl = ossUtil.uploadBase64(avatarUrl, "avatar");
        // 3. 更新数据库
        sysUserService.updateAvatar(userId, newUrl);
        // 4. 删除旧头像（OSS 文件）
        if (oldAvatar != null && !oldAvatar.isBlank()) {
            ossUtil.deleteByUrl(oldAvatar);
        }
        return R.ok();
    }

    @Operation(summary = "获取当前登录用户信息", description = "从 JWT Token 解析用户信息，查数据库补齐用户资料")
    @GetMapping("/me")
    public R<LoginVO> me() {
        Long userId = UserContext.getUserId();
        String username = UserContext.getUsername();
        SysUser user = sysUserService.getById(userId);
        return R.ok(LoginVO.builder()
                .userId(userId)
                .username(username)
                .realName(user != null ? DesensitizeUtil.name(user.getRealName()) : null)
                .avatar(user != null ? user.getAvatar() : null)
                .nickname(user != null ? user.getNickname() : null)
                .gender(user != null ? user.getGender() : null)
                .birthday(user != null ? user.getBirthday() : null)
                .signature(user != null ? user.getSignature() : null)
                .isPerfect(user != null ? user.getIsPerfect() : null)
                .status(user != null ? user.getStatus() : null)
                .lastLogin(user != null ? user.getLastLogin() : null)
                .roles(UserContext.getRoles())
                .permissions(UserContext.getPermissions())
                .build());
    }

    // ========== 实名认证 & 安全资料 ==========

    @Operation(summary = "获取实名&安全资料", description = "返回已脱敏的实名状态、手机号、邮箱信息")
    @GetMapping("/profile")
    public R<SecurityProfileVO> securityProfile() {
        return R.ok(sysUserService.getSecurityProfile(UserContext.getUserId()));
    }

    @Operation(summary = "提交实名认证", description = "真实姓名+身份证号，已实名不可重复提交")
    @PostMapping("/realname")
    public R<Void> submitRealName(@Valid @RequestBody RealNameAuthDTO dto) {
        sysUserService.submitRealName(UserContext.getUserId(), dto);
        return R.ok();
    }

    @Operation(summary = "绑定/修改手机号", description = "需短信验证码验证，手机号全局唯一")
    @PutMapping("/phone")
    public R<Void> bindPhone(@Valid @RequestBody BindPhoneDTO dto) {
        sysUserService.bindPhone(UserContext.getUserId(), dto);
        return R.ok();
    }

    @Operation(summary = "绑定/修改邮箱", description = "需邮箱验证码验证")
    @PutMapping("/email")
    public R<Void> bindEmail(@Valid @RequestBody BindEmailDTO dto) {
        sysUserService.bindEmail(UserContext.getUserId(), dto);
        return R.ok();
    }

    @Operation(summary = "发送邮箱验证码", description = "60秒内不可重复发送，真实发送到邮箱")
    @PostMapping("/send-email")
    public R<Void> sendEmailCode(@RequestParam String email) {
        sysUserService.sendEmailCode(email);
        return R.ok();
    }

    @Operation(summary = "校验邮箱验证码", description = "校验通过后标记该邮箱已验证（有效期10分钟），用于找回密码")
    @PostMapping("/verify-email-code")
    public R<Boolean> verifyEmailCode(@RequestParam String email, @RequestParam String code) {
        return R.ok(sysUserService.verifyEmailCode(email, code));
    }

    @Operation(summary = "检查邮箱是否已绑定")
    @GetMapping("/check-email")
    public R<Boolean> checkEmail(@RequestParam String email) {
        return R.ok(sysUserService.checkEmailExists(email));
    }

    @Operation(summary = "找回密码", description = "通过手机号或邮箱验证后重置密码，无需旧密码。验证码须先调用 verify-code / verify-email-code 校验通过")
    @PostMapping("/reset-password")
    public R<Void> resetPassword(@Valid @RequestBody ResetPasswordDTO dto) {
        sysUserService.resetPassword(dto.getPhone(), dto.getEmail(), dto.getCode(), dto.getNewPassword());
        return R.ok();
    }

    @Operation(summary = "发送原手机号验证码", description = "换绑手机号时向当前绑定手机号发送验证码，验证本人操作")
    @PostMapping("/send-old-phone-code")
    public R<Void> sendOldPhoneCode() {
        sysUserService.sendOldPhoneCode(UserContext.getUserId());
        return R.ok();
    }

    @Operation(summary = "发送原邮箱验证码", description = "换绑邮箱时向当前绑定邮箱发送验证码，验证本人操作")
    @PostMapping("/send-old-email-code")
    public R<Void> sendOldEmailCode() {
        sysUserService.sendOldEmailCode(UserContext.getUserId());
        return R.ok();
    }

    @Operation(summary = "权限测试接口", description = "需要 spu:delete 权限才能访问")
    @RequirePermission("spu:delete")
    @DeleteMapping("/test/permission")
    public R<String> testPermission() {
        return R.ok("权限校验通过，你拥有 spu:delete 权限");
    }
}
