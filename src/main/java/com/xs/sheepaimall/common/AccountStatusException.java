package com.xs.sheepaimall.common;

/**
 * 账号状态异常（禁用/锁定等）
 */
public class AccountStatusException extends RuntimeException {

    public AccountStatusException(String message) {
        super(message);
    }
}
