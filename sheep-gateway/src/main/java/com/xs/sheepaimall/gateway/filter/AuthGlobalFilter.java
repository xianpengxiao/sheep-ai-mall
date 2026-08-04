package com.xs.sheepaimall.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.List;

/**
 * Gateway 全局 JWT 过滤器：白名单放行 + Token 校验 + 用户信息透传 Header。
 * <p>Redis 黑名单校验由各业务服务的 UserContextInterceptor 完成。</p>
 */
@Slf4j
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final String TOKEN_PREFIX = "Bearer ";

    /** 白名单路径 */
    private static final List<String> WHITE_PATHS = List.of(
            "/api/auth/login", "/api/auth/register", "/api/auth/send-code",
            "/api/auth/verify-code", "/api/auth/check-phone", "/api/auth/sms-login",
            "/api/auth/send-login-code", "/api/auth/send-email", "/api/auth/verify-email-code",
            "/api/auth/check-email", "/api/auth/reset-password",
            "/api/payment/notify"
    );

    /** 游客只读 GET 路径 */
    private static final List<String> PUBLIC_GET_PATTERNS = List.of(
            "/api/category/tree", "/api/category/children", "/api/category/",
            "/api/spu/page", "/api/spu/hot", "/api/spu/",
            "/api/sku/spu/", "/api/sku/",
            "/api/search/product", "/api/search/merchant",
            "/api/merchant/", "/api/product/",
            "/swagger-ui", "/v3/api-docs", "/swagger-resources", "/webjars"
    );

    private final SecretKey secretKey;

    public AuthGlobalFilter(@Value("${sheep.jwt.secret:SheepAIMall-JWT-Secret-Key-2026-For-Auth}") String secret) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes.length >= 32 ? keyBytes :
                sha256Digest(secret.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 白名单路径放行
        if (WHITE_PATHS.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        // 游客只读 GET 请求放行
        if ("GET".equalsIgnoreCase(exchange.getRequest().getMethod().name())
                && PUBLIC_GET_PATTERNS.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        // 提取并校验 Token
        String token = extractToken(exchange.getRequest());
        if (token == null) {
            return unauthorized(exchange, "未提供认证令牌，请先登录");
        }

        Claims claims;
        try {
            claims = parseToken(token);
        } catch (ExpiredJwtException e) {
            return unauthorized(exchange, "登录已过期，请重新登录");
        } catch (MalformedJwtException | SignatureException | IllegalArgumentException e) {
            return unauthorized(exchange, "认证令牌无效");
        }

        // 用户信息透传给下游服务
        Object permsObj = claims.get("perms");
        String permsStr = permsObj instanceof List ? String.join(",", (List<String>) permsObj) : "";
        Object rolesObj = claims.get("roles");
        String rolesStr = rolesObj instanceof List ? String.join(",", (List<String>) rolesObj) : "";
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header("X-User-Id", claims.getSubject())
                .header("X-Username", claims.get("username", String.class))
                .header("X-Permissions", permsStr)
                .header("X-Roles", rolesStr)
                .build();

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    /*
    * 优先级顺序排序，值越小优先级越高
    * */
    @Override
    public int getOrder() {
        return -100;
    }

    private String extractToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(TOKEN_PREFIX)) {
            return header.substring(TOKEN_PREFIX.length()).trim();
        }
        return null;
    }

    private Claims parseToken(String token) {
        return Jwts.parser().verifyWith(secretKey).build()
                .parseSignedClaims(token).getPayload();
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String msg) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":401,\"msg\":\"" + msg + "\",\"data\":null}";
        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private byte[] sha256Digest(byte[] raw) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(raw);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 算法不可用", e);
        }
    }
}
