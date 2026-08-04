package com.xs.sheepaimall.feign;

import com.xs.sheepaimall.entity.SysUser;
import com.xs.sheepaimall.entity.SysUserRole;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 认证服务内部 Feign 接口
 */
@FeignClient(name = "sheep-auth", path = "/internal/auth")
public interface AuthFeignClient {

    @GetMapping("/users/{id}")
    SysUser getUserById(@PathVariable Long id);

    @GetMapping("/users/by-username")
    SysUser getUserByUsername(@RequestParam String username);

    @GetMapping("/users/list-by-ids")
    List<SysUser> listUsersByIds(@RequestParam List<Long> ids);

    @GetMapping("/users/list-by-keyword")
    List<Long> listUserIdsByKeyword(@RequestParam String keyword);

    @PostMapping("/user-role")
    void insertUserRole(@RequestBody SysUserRole userRole);
}
