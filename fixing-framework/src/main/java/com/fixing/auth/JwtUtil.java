package com.fixing.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具：签发与解析令牌。
 *
 * <p>JWT = header.payload.signature 三段 Base64：payload 放 userId 和过期时间，
 * signature 用服务端密钥防篡改。payload 只是编码不是加密 —— 绝不放敏感信息。
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expireMillis;

    /** 密钥 ≥32 字节；生产必须用环境变量注入随机密钥 */
    public JwtUtil(@Value("${fixing.jwt.secret}") String secret,
                   @Value("${fixing.jwt.expire-hours}") long expireHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMillis = expireHours * 3600_000L;
    }

    /** 登录成功后签发：subject 存 userId */
    public String createToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis))
                .signWith(key)
                .compact();
    }

    /** 解析 userId；签名不对/过期/格式错统一返回 null，由拦截器转 401 */
    public Long parseUserId(String token) {
        Claims claims = parseClaims(token);
        return claims == null ? null : Long.valueOf(claims.getSubject());
    }

    /** 令牌剩余有效毫秒数（登出黑名单的 TTL 用）；无效令牌返回 0 */
    public long remainingMillis(String token) {
        Claims claims = parseClaims(token);
        if (claims == null || claims.getExpiration() == null) {
            return 0;
        }
        return Math.max(0, claims.getExpiration().getTime() - System.currentTimeMillis());
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
