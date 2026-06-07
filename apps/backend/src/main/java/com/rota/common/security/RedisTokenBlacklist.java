package com.rota.common.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis-backed {@link TokenBlacklist}. Each revoked {@code jti} is a key that auto-expires when
 * the token would have expired anyway, so the set never grows unbounded. Only active when
 * {@code rota.token-blacklist.enabled=true} (so tests / Redis-less envs are unaffected).
 */
@Component
@ConditionalOnProperty(prefix = "rota.token-blacklist", name = "enabled", havingValue = "true")
public class RedisTokenBlacklist implements TokenBlacklist {

    private static final String PREFIX = "bl:jti:";

    private final StringRedisTemplate redis;

    public RedisTokenBlacklist(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void blacklist(String jti, Duration ttl) {
        if (jti == null || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return;
        }
        redis.opsForValue().set(PREFIX + jti, "1", ttl);
    }

    @Override
    public boolean isBlacklisted(String jti) {
        return jti != null && Boolean.TRUE.equals(redis.hasKey(PREFIX + jti));
    }
}
