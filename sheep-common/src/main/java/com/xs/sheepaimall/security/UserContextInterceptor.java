package com.xs.sheepaimall.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户上下文拦截器：从 Gateway 透传的 Header 中还原用户信息到 ThreadLocal。
 * <p>各业务服务通过 WebConfig 注册此拦截器。</p>
 */
@Slf4j
public class UserContextInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.isEmpty()) {
            UserContext.setUserId(Long.valueOf(userId));
            UserContext.setUsername(request.getHeader("X-Username"));
            String perms = request.getHeader("X-Permissions");
            if (perms != null && !perms.isEmpty()) {
                UserContext.setPermissions(Arrays.asList(perms.split(",")));
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }
}
