package com.xs.sheepaimall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xs.sheepaimall.common.R;
import com.xs.sheepaimall.dto.AssignRolesDTO;
import com.xs.sheepaimall.dto.SpuAuditDTO;
import com.xs.sheepaimall.entity.Spu;
import com.xs.sheepaimall.entity.SysRole;
import com.xs.sheepaimall.entity.SysUser;
import com.xs.sheepaimall.security.RequirePermission;
import com.xs.sheepaimall.service.SysUserService;
import com.xs.sheepaimall.vo.RoleVO;
import com.xs.sheepaimall.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 管理员接口：用户管理、角色管理、权限分配
 */
@Tag(name = "管理员", description = "用户管理、角色查看、角色分配（需管理员权限）")
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Resource
    private SysUserService sysUserService;

    @Resource
    private com.xs.sheepaimall.service.SpuService spuService;

    // ==================== 用户管理 ====================

    @Operation(summary = "分页查询用户列表", description = "支持按用户名/真实姓名/手机号模糊搜索")
    @RequirePermission("sys:user:list")
    @GetMapping("/users")
    public R<Page<UserVO>> listUsers(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "搜索关键字") @RequestParam(required = false) String keyword) {
        Page<SysUser> userPage = sysUserService.listUsers(page, size, keyword);
        // 转换为 UserVO，填充角色信息
        Page<UserVO> voPage = new Page<>(page, size, userPage.getTotal());
        List<UserVO> voList = userPage.getRecords().stream().map(user -> {
            List<String> roles = sysUserService.getUserRoleCodes(user.getId());
            return UserVO.builder()
                    .id(user.getId())
                    .username(user.getUsername())
                    .realName(user.getRealName())
                    .phone(user.getPhone())
                    .email(user.getEmail())
                    .avatar(user.getAvatar())
                    .status(user.getStatus())
                    .lastLogin(user.getLastLogin())
                    .remark(user.getRemark())
                    .roles(roles)
                    .createTime(user.getCreateTime())
                    .updateTime(user.getUpdateTime())
                    .build();
        }).collect(Collectors.toList());
        voPage.setRecords(voList);
        return R.ok(voPage);
    }

    @Operation(summary = "查询用户的角色ID列表")
    @RequirePermission("sys:user:list")
    @GetMapping("/users/{userId}/roles")
    public R<List<Long>> getUserRoles(
            @Parameter(description = "用户ID") @PathVariable Long userId) {
        return R.ok(sysUserService.getUserRoleIds(userId));
    }

    @Operation(summary = "为用户分配角色", description = "替换模式：先清空该用户所有角色，再分配传入的角色ID列表")
    @RequirePermission("sys:user:update")
    @PutMapping("/users/{userId}/roles")
    public R<Void> assignRoles(
            @Parameter(description = "用户ID") @PathVariable Long userId,
            @Valid @RequestBody AssignRolesDTO dto) {
        sysUserService.assignRoles(userId, dto.getRoleIds());
        return R.ok();
    }

    // ==================== 角色管理 ====================

    @Operation(summary = "查询所有可用角色")
    @RequirePermission("sys:role:list")
    @GetMapping("/roles")
    public R<List<RoleVO>> listRoles() {
        List<RoleVO> roles = sysUserService.listAllRoles().stream()
                .map(role -> RoleVO.builder()
                        .id(role.getId())
                        .roleCode(role.getRoleCode())
                        .roleName(role.getRoleName())
                        .description(role.getDescription())
                        .sortOrder(role.getSortOrder())
                        .build())
                .collect(Collectors.toList());
        return R.ok(roles);
    }

    // ==================== 商品审核 ====================

    @Operation(summary = "待审核商品列表")
    @RequirePermission("spu:audit:list")
    @GetMapping("/spu/pending-audit")
    public R<Page<Spu>> pendingAuditSpu(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(spuService.pagePendingAudit(pageNum, pageSize));
    }

    @Operation(summary = "审核商品（通过/驳回）", description = "通过后自动上架，驳回需填写原因")
    @RequirePermission("spu:audit")
    @PutMapping("/spu/audit")
    public R<Void> auditSpu(@Valid @RequestBody SpuAuditDTO dto) {
        spuService.auditSpu(dto.getSpuId(), dto.getAuditStatus(), dto.getAuditMsg());
        return R.ok();
    }
}
