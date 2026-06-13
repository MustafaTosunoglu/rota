package com.rota.proxy.internal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Redis-backed daily quota. One counter per (tenant, UTC date) with a 24h TTL; INCR is atomic
 * so concurrent Try Its count correctly. Active only when {@code rota.proxy.daily-limit-enabled}
 * (off in tests, where a {@link NoopTryItQuota} is used instead).
 */
@Component
@ConditionalOnProperty(prefix = "rota.proxy", name = "daily-limit-enabled", havingValue = "true")
class RedisTryItQuota implements TryItQuota {

    private final StringRedisTemplate redis;
    private final ProxyProperties properties;

    RedisTryItQuota(StringRedisTemplate redis, ProxyProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public void consume(UUID tenantId) {
        String key = key(tenantId);
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofHours(24));
        }
        if (count != null && count > properties.getDailyFreeLimit()) {
            throw new QuotaExceededException(properties.getDailyFreeLimit());
        }
    }

    @Override
    public long remaining(UUID tenantId) {
        String value = redis.opsForValue().get(key(tenantId));
        long used = value == null ? 0 : Long.parseLong(value);
        return Math.max(0, properties.getDailyFreeLimit() - used);
    }

    private String key(UUID tenantId) {
        return "tryit:" + tenantId + ":" + LocalDate.now(ZoneOffset.UTC);
    }
}
