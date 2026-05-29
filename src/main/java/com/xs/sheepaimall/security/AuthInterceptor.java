package com.xs.sheepaimall.security;

import com.xs.sheepaimall.common.ForbiddenException;
import com.xs.sheepaimall.common.UnauthorizedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * JWT 认证拦截器
 * <p>
 * 流程：
 * 1. 从请求头 Authorization 提取 Bearer Token
 * 2. 解析校验 JWT，提取用户信息 → ThreadLocal
 * 3. 检查 Redis 黑名单（退出登录的 Token 不可复用）
 * 4. 检查方法上的 @RequirePermission 注解，校验权限
 * 5. afterCompletion 中清理 ThreadLocal
 */
@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {

    /** HTTP 请求头中的 Token 前缀 */
    private static final String TOKEN_PREFIX = "Bearer ";
    /** Redis 黑名单 Key 前缀 */
    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;

    public AuthInterceptor(JwtUtil jwtUtil, StringRedisTemplate stringRedisTemplate) {
        this.jwtUtil = jwtUtil;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 非 Controller 方法（如静态资源）直接放行
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        String token = extractToken(request);
        if (token == null) {
            throw new UnauthorizedException("未提供认证令牌，请先登录");
        }

        // 1. 解析 JWT（过期/格式错误/签名错误均抛出异常，由 GlobalExceptionHandler 统一处理）
        Claims claims;
        try {
            claims = jwtUtil.parseToken(token);
        } catch (ExpiredJwtException e) {
            throw new UnauthorizedException("登录已过期，请重新登录");
        } catch (MalformedJwtException | SignatureException | IllegalArgumentException e) {
            throw new UnauthorizedException("认证令牌无效");
        }

        // 2. 检查 Redis 黑名单（已退出登录的 Token）
        String blacklistKey = buildBlacklistKey(token);
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(blacklistKey))) {
            throw new UnauthorizedException("令牌已失效，请重新登录");
        }

        // 3. 双重检查过期
        if (jwtUtil.isExpired(claims)) {
            throw new UnauthorizedException("登录已过期，请重新登录");
        }

        // 4. 提取用户信息存入 ThreadLocal
        Long userId = jwtUtil.getUserId(claims);
        String username = jwtUtil.getUsername(claims);
        List<String> permissions = jwtUtil.getPermissions(claims);

        UserContext.setUserId(userId);
        UserContext.setUsername(username);
        UserContext.setPermissions(permissions);
        UserContext.setToken(token); // 供退出登录时获取

        // 5. 检查方法权限注解
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        RequirePermission requirePermission = handlerMethod.getMethodAnnotation(RequirePermission.class);
        if (requirePermission != null) {
            checkPermission(requirePermission.value(), permissions, username);
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 请求结束清理 ThreadLocal，防止内存泄漏
        UserContext.clear();
    }

    /**
     * 从请求头提取 Bearer Token
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(TOKEN_PREFIX)) {
            return header.substring(TOKEN_PREFIX.length()).trim();
        }
        return null;
    }

    /**
     * 校验权限：用户拥有的权限与注解要求的权限取交集，为空则拒绝
     */
    private void checkPermission(String[] requiredPerms, List<String> userPerms, String username) {
        if (userPerms == null || userPerms.isEmpty()) {
            log.warn("用户 {} 无任何权限，拒绝访问，要求：{}", username, (Object) requiredPerms);
            throw new ForbiddenException("权限不足，无法访问该资源");
        }
        for (String required : requiredPerms) {
            if (userPerms.contains(required)) {
                return;
            }
        }
        log.warn("用户 {} 权限不足，拥有：{}，要求：{}", username, userPerms, requiredPerms);
        throw new ForbiddenException("权限不足，无法访问该资源");
    }

    /**
     * 构建 Token 的黑名单 Redis Key（MD5 摘要缩短长度）
     */
    public static String buildBlacklistKey(String token) {
        return BLACKLIST_PREFIX + md5(token);
    }

    /**
     * 计算字符串的 MD5 十六进制摘要
     */
    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 算法不可用", e);
        }
    }
}
