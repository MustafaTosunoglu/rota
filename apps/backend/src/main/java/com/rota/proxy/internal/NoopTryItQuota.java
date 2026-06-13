package com.rota.proxy.internal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

/** Fallback quota when the daily limit is disabled (e.g. tests): always allows. */
@Configuration
class NoopTryItQuota {

    @Bean
    @ConditionalOnMissingBean(TryItQuota.class)
    TryItQuota unlimitedTryItQuota(ProxyProperties properties) {
        return new TryItQuota() {
            @Override
            public void consume(UUID tenantId) {
                // no limit
            }

            @Override
            public long remaining(UUID tenantId) {
                return properties.getDailyFreeLimit();
            }
        };
    }
}
