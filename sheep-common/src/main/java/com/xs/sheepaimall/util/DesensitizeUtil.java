package com.xs.sheepaimall.util;

/**
 * 敏感信息脱敏工具
 */
public final class DesensitizeUtil {

    private DesensitizeUtil() {}

    /** 姓名：张** */
    public static String name(String name) {
        if (name == null || name.length() < 2) return name;
        return name.charAt(0) + "*".repeat(name.length() - 1);
    }

    /** 身份证：110101******1234 */
    public static String idCard(String id) {
        if (id == null || id.length() < 10) return id;
        return id.substring(0, 6) + "******" + id.substring(id.length() - 4);
    }

    /** 手机号：138****5678 */
    public static String phone(String phone) {
        if (phone == null || phone.length() < 11) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    /** 邮箱：ab***@example.com */
    public static String email(String email) {
        if (email == null || !email.contains("@")) return email;
        int at = email.indexOf("@");
        String local = email.substring(0, at);
        if (local.length() <= 2) return local + "***" + email.substring(at);
        return local.substring(0, 2) + "***" + email.substring(at);
    }
}
