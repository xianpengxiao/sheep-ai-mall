package com.xs.sheepaimall.controller;

import com.xs.sheepaimall.common.ForbiddenException;
import com.xs.sheepaimall.common.R;
import com.xs.sheepaimall.security.UserContext;
import com.xs.sheepaimall.util.OssUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 统一图片上传接口 — 全场景复用（用户头像、商品图片、商家资质）
 */
@Tag(name = "上传", description = "统一图片上传，支持 MultipartFile 和 Base64 两种格式")
@Validated
@RestController
@RequestMapping("/api/upload")
public class UploadController {

    /** goods/cert 类型需要的权限标识 */
    private static final String PERM_GOODS = "merchant:goods:manage";
    private static final String PERM_CERT = "merchant:info:update";

    @Resource
    private OssUtil ossUtil;

    @Operation(summary = "上传图片",
            description = "支持二进制文件(file)和Base64(avatarUrl)两种入参，type=avatar/goods/cert 按场景分目录存储")
    @PostMapping("/image")
    public R<String> uploadImage(
            @Parameter(description = "二进制图片文件") @RequestParam(required = false) MultipartFile file,
            @Parameter(description = "Base64 图片数据（兼容 data:image/xxx;base64, 前缀格式）") @RequestParam(required = false) String avatarUrl,
            @Parameter(description = "图片分类：avatar=头像 goods=商品图片 cert=商家资质", required = true) @RequestParam String type) {

        // 1. 按 type 校验权限
        checkUploadPermission(type);

        // 2. 二选一上传
        if (file != null && !file.isEmpty()) {
            return R.ok(ossUtil.upload(file, type));
        }
        if (avatarUrl != null && !avatarUrl.isBlank()) {
            return R.ok(ossUtil.uploadBase64(avatarUrl, type));
        }
        return R.fail("请提供 file 或 avatarUrl 参数");
    }

    /**
     * 按 type 校验上传权限
     */
    private void checkUploadPermission(String type) {
        if ("avatar".equals(type)) {
            return; // 仅需登录（AuthInterceptor 已拦截未登录请求）
        }
        List<String> perms = UserContext.getPermissions();
        if (perms == null || perms.isEmpty()) {
            throw new ForbiddenException("权限不足，无法上传该类型图片");
        }
        String required = "goods".equals(type) ? PERM_GOODS : PERM_CERT;
        if (!perms.contains(required)) {
            throw new ForbiddenException("权限不足，无法上传该类型图片");
        }
    }
}
