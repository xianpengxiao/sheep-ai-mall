package com.xs.sheepaimall.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;

/**
 * JWT 工具类：令牌生成、解析、校验
 */
@Slf4j
@Component
public class JwtUtil {

    /** JWT Claims 中的权限字段名 */
    public static final String CLAIM_PERMISSIONS = "perms";
    /** JWT Claims 中的用户名字段名 */
    public static final String CLAIM_USERNAME = "username";

    private final SecretKey secretKey;
    private final long expiration;

    /**
     * 构造时从配置文件读取密钥和过期时间，初始化 HMAC-SHA 密钥
     */
    public JwtUtil(@Value("${sheep.jwt.secret}") String secret,
                   @Value("${sheep.jwt.expiration}") long expiration) {
        // 确保密钥至少 256 位（32 字节），不足则用 SHA-256 摘要填充
        byte[] keyBytes = Decoders.BASE64.decode(
                java.util.Base64.getEncoder().encodeToString(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8))
        );
        // 对原始密钥做 HMAC-SHA256 派生，确保长度 ≥ 256 bits
        this.secretKey = Keys.hmacShaKeyFor(
                keyBytes.length >= 32 ? keyBytes : ensureMinLength(secret)
        );
        this.expiration = expiration;
        log.info("JWT 工具初始化完成，过期时间：{} 秒", expiration);
    }

    /**
     * 生成 JWT 令牌
     *
     * @param userId      用户ID
     * @param username    登录账号
     * @param permissions 权限标识列表
     * @return JWT 字符串
     */
    public String generateToken(Long userId, String username, List<String> permissions) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration * 1000);

        return Jwts.builder()
                .subject(String.valueOf(userId))           // sub = 用户ID
                .claim(CLAIM_USERNAME, username)           // 自定义：用户名
                .claim(CLAIM_PERMISSIONS, permissions)     // 自定义：权限列表
                .issuedAt(now)                             // 签发时间
                .expiration(expiryDate)                    // 过期时间
                .signWith(secretKey)                       // HMAC-SHA256 签名
                .compact();
    }

    /**
     * 解析并校验 JWT 令牌，返回 Claims
     *
     * @param token JWT 字符串
     * @return 解析后的 Claims
     * @throws ExpiredJwtException      令牌过期
     * @throws MalformedJwtException    令牌格式错误
     * @throws SignatureException       签名校验失败
     * @throws IllegalArgumentException 令牌为空
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从 Claims 中提取用户ID
     */
    public Long getUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    /**
     * 从 Claims 中提取用户名
     */
    public String getUsername(Claims claims) {
        return claims.get(CLAIM_USERNAME, String.class);
    }

    /**
     * 从 Claims 中提取权限列表
     */
    @SuppressWarnings("unchecked")
    public List<String> getPermissions(Claims claims) {
        return claims.get(CLAIM_PERMISSIONS, List.class);
    }

    /**
     * 判断令牌是否已过期
     */
    public boolean isExpired(Claims claims) {
        return claims.getExpiration().before(new Date());
    }

    /**
     * 确保密钥长度 ≥ 256 bits（对短密钥做 HMAC-SHA256 派生）
     */
    private byte[] ensureMinLength(String raw) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            return md.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 算法不可用", e);
        }
    }
}
