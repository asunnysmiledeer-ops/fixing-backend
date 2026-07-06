package com.fixing.auth;

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
 * <p>JWT = header.payload.signature 三段 Base64：
 * - payload 里放 userId（subject）和过期时间 —— 服务端不用存会话，无状态；
 * - signature 用服务端密钥对前两段签名 —— 客户端改不了内容（改了签名对不上）。
 *
 * <p>注意 payload 只是编码不是加密，谁都能解开看，所以里面绝不放密码等敏感信息。
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expireMillis;

    /**
     * 密钥与有效期从配置读入。HMAC-SHA256 要求密钥 ≥ 32 字节，
     * 生产环境必须用环境变量注入随机密钥，绝不能用仓库里的默认值。
     */
    public JwtUtil(@Value("${fixing.jwt.secret}") String secret,
                   @Value("${fixing.jwt.expire-hours}") long expireHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMillis = expireHours * 3600_000L;
    }

    /** 登录成功后签发：subject 存 userId，带过期时间 */
    public String createToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis))
                .signWith(key)
                .compact();
    }

    /**
     * 解析令牌返回 userId。
     * 签名不对（被篡改）/ 已过期 / 格式错，统一返回 null，由拦截器转成 401。
     */
    public Long parseUserId(String token) {
        try {
            String subject = Jwts.parser()
                    .verifyWith(key)          // 用同一把密钥验签
                    .build()
                    .parseSignedClaims(token) // 过期/篡改都会在这里抛异常
                    .getPayload()
                    .getSubject();
            return Long.valueOf(subject);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
