package com.xs.sheepaimall.security;

import java.lang.annotation.*;

/**
 * 接口权限注解：标注在 Controller 方法上，指定访问所需的权限标识。
 * 支持多个权限，满足其一即可（OR 逻辑）。
 *
 * <pre>
 * // 单个权限
 * &#64;RequirePermission("spu:delete")
 *
 * // 多个权限（OR 逻辑：拥有任一权限即可访问）
 * &#64;RequirePermission({"spu:create", "spu:update"})
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {

    /** 权限标识数组，满足其一即可 */
    String[] value();
}
