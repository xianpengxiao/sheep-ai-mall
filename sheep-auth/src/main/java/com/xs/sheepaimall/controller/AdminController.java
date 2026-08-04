package com.xs.sheepaimall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xs.sheepaimall.common.R;
import com.xs.sheepaimall.dto.AssignRolesDTO;
import com.xs.sheepaimall.entity.SysUser;
import com.xs.sheepaimall.security.RequirePermission;
import com.xs.sheepaimall.service.SysUserService;
import com.xs.sheepaimall.vo.RoleVO;
import com.xs.sheepaimall.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 管理员接口：用户管理、角色管理、权限分配（sheep-auth 服务）
 */
@Tag(name = "管理员-用户角色", description = "用户管理、角色查看、角色分配")
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private SysUserService sysUserService;

    // ==================== 用户管理 ====================

    @Operation(summary = "分页查询用户列表")
    @RequirePermission("sys:user:list")
    @GetMapping("/users")
    public R<Page<UserVO>> listUsers(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "搜索关键字") @RequestParam(required = false) String keyword) {
        Page<SysUser> userPage = sysUserService.listUsers(page, size, keyword);
        Page<UserVO> voPage = new Page<>(page, size, userPage.getTotal());
        List<UserVO> voList = userPage.getRecords().stream().map(user -> {
            List<String> roles = sysUserService.getUserRoleCodes(user.getId());
            return UserVO.builder()
                    .id(user.getId()).username(user.getUsername())
                    .realName(user.getRealName()).nickname(user.getNickname())
                    .gender(user.getGender()).birthday(user.getBirthday())
                    .signature(user.getSignature()).idCard(user.getIdCard())
                    .isPerfect(user.getIsPerfect()).phone(user.getPhone())
                    .email(user.getEmail()).avatar(user.getAvatar())
                    .status(user.getStatus()).lastLogin(user.getLastLogin())
                    .remark(user.getRemark()).roles(roles)
                    .createTime(user.getCreateTime()).updateTime(user.getUpdateTime())
                    .build();
        }).collect(Collectors.toList());
        voPage.setRecords(voList);
        return R.ok(voPage);
    }

    @Operation(summary = "查询用户的角色ID列表")
    @RequirePermission("sys:user:list")
    @GetMapping("/users/{userId}/roles")
    public R<List<Long>> getUserRoles(@PathVariable Long userId) {
        return R.ok(sysUserService.getUserRoleIds(userId));
    }

    @Operation(summary = "为用户分配角色")
    @RequirePermission("sys:user:update")
    @PutMapping("/users/{userId}/roles")
    public R<Void> assignRoles(@PathVariable Long userId, @Valid @RequestBody AssignRolesDTO dto) {
        sysUserService.assignRoles(userId, dto.getRoleIds());
        return R.ok();
    }

    @Operation(summary = "启用/禁用用户")
    @RequirePermission("sys:user:update")
    @PutMapping("/users/{id}/status")
    public R<Void> updateUserStatus(@PathVariable Long id, @RequestParam Integer status) {
        sysUserService.updateStatus(id, status);
        return R.ok();
    }

    // ==================== 角色管理 ====================

    @Operation(summary = "查询所有可用角色")
    @RequirePermission("sys:role:list")
    @GetMapping("/roles")
    public R<List<RoleVO>> listRoles() {
        List<RoleVO> roles = sysUserService.listAllRoles().stream()
                .map(role -> RoleVO.builder()
                        .id(role.getId()).roleCode(role.getRoleCode())
                        .roleName(role.getRoleName()).description(role.getDescription())
                        .sortOrder(role.getSortOrder()).build())
                .collect(Collectors.toList());
        return R.ok(roles);
    }
}
