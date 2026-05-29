package com.xs.sheepaimall.config;

import com.xs.sheepaimall.common.*;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器：统一将各类异常转为标准化 JSON 响应
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ==================== 认证授权异常 ====================

    /** 未登录 / Token 无效 / Token 过期 */
    @ExceptionHandler(UnauthorizedException.class)
    public R<Void> handleUnauthorizedException(UnauthorizedException e) {
        log.warn("未授权访问: {}", e.getMessage());
        return R.fail(ResultCode.UNAUTHORIZED.getCode(), e.getMessage());
    }

    /** JWT 令牌过期 */
    @ExceptionHandler(ExpiredJwtException.class)
    public R<Void> handleExpiredJwtException(ExpiredJwtException e) {
        log.warn("JWT 令牌过期: {}", e.getMessage());
        return R.fail(ResultCode.UNAUTHORIZED.getCode(), "登录已过期，请重新登录");
    }

    /** JWT 签名异常 */
    @ExceptionHandler(SignatureException.class)
    public R<Void> handleSignatureException(SignatureException e) {
        log.warn("JWT 签名校验失败: {}", e.getMessage());
        return R.fail(ResultCode.UNAUTHORIZED.getCode(), "认证令牌无效");
    }

    /** JWT 格式错误 */
    @ExceptionHandler(MalformedJwtException.class)
    public R<Void> handleMalformedJwtException(MalformedJwtException e) {
        log.warn("JWT 格式错误: {}", e.getMessage());
        return R.fail(ResultCode.UNAUTHORIZED.getCode(), "认证令牌格式错误");
    }

    /** 权限不足 */
    @ExceptionHandler(ForbiddenException.class)
    public R<Void> handleForbiddenException(ForbiddenException e) {
        log.warn("权限不足: {}", e.getMessage());
        return R.fail(ResultCode.FORBIDDEN.getCode(), e.getMessage());
    }

    /** 账号状态异常（禁用/锁定） */
    @ExceptionHandler(AccountStatusException.class)
    public R<Void> handleAccountStatusException(AccountStatusException e) {
        log.warn("账号状态异常: {}", e.getMessage());
        return R.fail(ResultCode.FORBIDDEN.getCode(), e.getMessage());
    }

    // ==================== 业务异常 ====================

    /** 业务异常 */
    @ExceptionHandler(BizException.class)
    public R<Void> handleBizException(BizException e) {
        log.warn("业务异常: {}", e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    // ==================== 参数校验异常 ====================

    /** @Valid 参数校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleValidationException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        log.warn("参数校验失败: {}", msg);
        return R.fail(ResultCode.PARAM_ERROR.getCode(), msg);
    }

    // ==================== 兜底 ====================

    /** 兜底异常（未预期的运行时异常） */
    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return R.fail(ResultCode.FAIL.getCode(), "服务器内部错误");
    }
}
