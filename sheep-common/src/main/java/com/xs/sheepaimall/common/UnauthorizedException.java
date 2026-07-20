package com.xs.sheepaimall.common;

/**
 * 未登录 / Token 无效异常（HTTP 401）
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
