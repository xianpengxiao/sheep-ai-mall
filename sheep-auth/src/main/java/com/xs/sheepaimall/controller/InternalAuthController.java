package com.xs.sheepaimall.controller;

import com.xs.sheepaimall.entity.*;
import com.xs.sheepaimall.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 认证服务内部控制器（供 Feign 调用）
 */
@RestController
@RequestMapping("/internal/auth")
public class InternalAuthController {

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;

    @GetMapping("/users/{id}")
    public SysUser getUserById(@PathVariable Long id) {
        return sysUserMapper.selectById(id);
    }

    @GetMapping("/users/by-username")
    public SysUser getUserByUsername(@RequestParam String username) {
        return sysUserMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, username));
    }

    @GetMapping("/users/list-by-ids")
    public List<SysUser> listUsersByIds(@RequestParam List<Long> ids) {
        return sysUserMapper.selectBatchIds(ids);
    }

    @GetMapping("/users/list-by-keyword")
    public List<Long> listUserIdsByKeyword(@RequestParam String keyword) {
        return sysUserMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                        .like(SysUser::getUsername, keyword)
                        .select(SysUser::getId)
        ).stream().map(SysUser::getId).collect(java.util.stream.Collectors.toList());
    }

    @PostMapping("/user-role")
    public void insertUserRole(@RequestBody SysUserRole userRole) {
        sysUserRoleMapper.insert(userRole);
    }
}
