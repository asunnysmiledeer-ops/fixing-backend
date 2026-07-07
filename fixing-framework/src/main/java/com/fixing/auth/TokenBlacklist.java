package com.fixing.auth;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/**
 * JWT 黑名单（Redis）—— 解决无状态令牌"登出后仍然有效"的安全缺陷。
 *
 * <p>原理：登出时把令牌指纹写入 Redis，TTL 设为令牌的剩余有效期 ——
 * 令牌自然过期后黑名单条目也自动消失，Redis 里永远只存"还活着但已作废"的令牌，
 * 不会无限膨胀。拦截器每次请求先查黑名单再验签。
 *
 * <p>存 SHA-256 指纹而不是令牌原文：Redis 被读走也拿不到可用令牌。
 */
@Component
public class TokenBlacklist {

    private static final String KEY_PREFIX = "fixing:jwt:blacklist:";

    private final StringRedisTemplate redis;
    private final JwtUtil jwtUtil;

    public TokenBlacklist(StringRedisTemplate redis, JwtUtil jwtUtil) {
        this.redis = redis;
        this.jwtUtil = jwtUtil;
    }

    /** 作废一枚令牌（登出/踢人共用入口） */
    public void revoke(String token) {
        long remainMillis = jwtUtil.remainingMillis(token);
        if (remainMillis <= 0) {
            return; // 已过期的令牌天然无效，不用进黑名单
        }
        redis.opsForValue().set(KEY_PREFIX + fingerprint(token), "1",
                Duration.ofMillis(remainMillis));
    }

    /** 拦截器调用：该令牌是否已被作废 */
    public boolean isRevoked(String token) {
        return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + fingerprint(token)));
    }

    private String fingerprint(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
