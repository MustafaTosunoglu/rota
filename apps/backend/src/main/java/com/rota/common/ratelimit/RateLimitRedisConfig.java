package com.rota.common.ratelimit;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * Builds the Bucket4j {@link ProxyManager} backed by a dedicated Lettuce connection (byte[]
 * codec, as Bucket4j requires). Only active when rate limiting is enabled — so tests and any
 * environment without Redis are unaffected.
 */
@Configuration
@ConditionalOnProperty(prefix = "rota.rate-limit", name = "enabled", havingValue = "true")
public class RateLimitRedisConfig {

    @Bean(destroyMethod = "shutdown")
    RedisClient rateLimitRedisClient(RedisProperties props) {
        RedisURI.Builder uri = RedisURI.builder()
                .withHost(props.getHost())
                .withPort(props.getPort());
        if (StringUtils.hasText(props.getUsername()) && StringUtils.hasText(props.getPassword())) {
            uri.withAuthentication(props.getUsername(), props.getPassword().toCharArray());
        } else if (StringUtils.hasText(props.getPassword())) {
            uri.withPassword(props.getPassword().toCharArray());
        }
        return RedisClient.create(uri.build());
    }

    @Bean(destroyMethod = "close")
    StatefulRedisConnection<byte[], byte[]> rateLimitRedisConnection(RedisClient client) {
        return client.connect(ByteArrayCodec.INSTANCE);
    }

    @Bean
    ProxyManager<byte[]> rateLimitProxyManager(StatefulRedisConnection<byte[], byte[]> connection,
                                               RateLimitProperties properties) {
        // Keep unused bucket keys from lingering: expire a little after a full refill window.
        Duration keepAfter = properties.getRefillPeriod().multipliedBy(2);
        return LettuceBasedProxyManager.builderFor(connection)
                .withExpirationStrategy(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(keepAfter))
                .build();
    }
}
