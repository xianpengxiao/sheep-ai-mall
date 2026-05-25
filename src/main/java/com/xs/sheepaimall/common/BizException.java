package com.xs.sheepaimall.common;

import lombok.Getter;

/**
 * 业务异常，抛出后由 GlobalExceptionHandler 统一处理
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String msg) {
        super(msg);
        this.code = ResultCode.BUSINESS_ERROR.getCode();
    }

    public BizException(int code, String msg) {
        super(msg);
        this.code = code;
    }

    public BizException(ResultCode resultCode) {
        super(resultCode.getMsg());
        this.code = resultCode.getCode();
    }
}
