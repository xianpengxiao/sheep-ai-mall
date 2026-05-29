package com.xs.sheepaimall.controller;

import com.xs.sheepaimall.common.R;
import com.xs.sheepaimall.dto.ChangePasswordDTO;
import com.xs.sheepaimall.dto.LoginDTO;
import com.xs.sheepaimall.dto.RegisterDTO;
import com.xs.sheepaimall.entity.SysUser;
import com.xs.sheepaimall.security.RequirePermission;
import com.xs.sheepaimall.security.UserContext;
import com.xs.sheepaimall.service.SysUserService;
import com.xs.sheepaimall.vo.LoginVO;
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

    @Operation(summary = "注册新账号", description = "创建系统用户，自动分配默认角色（只读）")
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

    @Operation(summary = "修改头像")
    @PutMapping("/avatar")
    public R<Void> updateAvatar(
            @Parameter(description = "头像URL") @RequestParam String avatarUrl) {
        sysUserService.updateAvatar(UserContext.getUserId(), avatarUrl);
        return R.ok();
    }

    @Operation(summary = "获取当前登录用户信息", description = "从 JWT Token 解析用户信息")
    @GetMapping("/me")
    public R<LoginVO> me() {
        Long userId = UserContext.getUserId();
        String username = UserContext.getUsername();
        return R.ok(LoginVO.builder()
                .userId(userId)
                .username(username)
                .roles(UserContext.getPermissions())
                .build());
    }

    @Operation(summary = "权限测试接口", description = "需要 spu:delete 权限才能访问")
    @RequirePermission("spu:delete")
    @DeleteMapping("/test/permission")
    public R<String> testPermission() {
        return R.ok("权限校验通过，你拥有 spu:delete 权限");
    }
}
