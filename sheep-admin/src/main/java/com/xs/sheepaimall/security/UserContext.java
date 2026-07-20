package com.xs.sheepaimall.security;

import java.util.List;

/**
 * 当前登录用户上下文（ThreadLocal 隔离，请求结束必须清理）
 */
public final class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();
    private static final ThreadLocal<List<String>> PERMISSIONS = new ThreadLocal<>();
    private static final ThreadLocal<List<String>> ROLES = new ThreadLocal<>();
    /** 当前请求的原始 JWT Token，供退出登录时获取 */
    private static final ThreadLocal<String> TOKEN = new ThreadLocal<>();

    private UserContext() {}

    public static void setUserId(Long userId)             { USER_ID.set(userId); }
    public static void setUsername(String username)       { USERNAME.set(username); }
    public static void setPermissions(List<String> perms) { PERMISSIONS.set(perms); }
    public static void setRoles(List<String> roles)       { ROLES.set(roles); }
    public static void setToken(String token)             { TOKEN.set(token); }

    public static Long getUserId()              { return USER_ID.get(); }
    public static String getUsername()          { return USERNAME.get(); }
    public static List<String> getPermissions() { return PERMISSIONS.get(); }
    public static List<String> getRoles()       { return ROLES.get(); }
    public static String getToken()             { return TOKEN.get(); }

    /** 请求结束后必须调用，防止内存泄漏 */
    public static void clear() {
        USER_ID.remove();
        USERNAME.remove();
        PERMISSIONS.remove();
        ROLES.remove();
        TOKEN.remove();
    }
}
