package com.xs.sheepaimall.controller;

import com.xs.sheepaimall.common.R;
import com.xs.sheepaimall.dto.UserProfileUpdateDTO;
import com.xs.sheepaimall.security.UserContext;
import com.xs.sheepaimall.service.SysUserService;
import com.xs.sheepaimall.vo.UserProfileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 用户资料管理
 */
@Tag(name = "用户资料", description = "用户信息查看与修改")
@Validated
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    @Resource
    private SysUserService sysUserService;

    @Operation(summary = "获取用户资料", description = "获取当前登录用户的完整资料（不含密码）")
    @GetMapping
    public R<UserProfileVO> getProfile() {
        Long userId = UserContext.getUserId();
        return R.ok(sysUserService.getProfile(userId));
    }

    @Operation(summary = "修改用户资料", description = "修改昵称/性别/生日/签名/头像，昵称含敏感词校验，首次完善资料自动标记 is_perfect=1")
    @PutMapping
    public R<UserProfileVO> updateProfile(@Valid @RequestBody UserProfileUpdateDTO dto) {
        Long userId = UserContext.getUserId();
        return R.ok(sysUserService.updateProfile(userId, dto));
    }
}
