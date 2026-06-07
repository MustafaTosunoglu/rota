package com.rota.common.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Per-IP throttle for the unauthenticated auth endpoints (plan §8.5). */
@Component
@ConfigurationProperties("rota.rate-limit")
public class RateLimitProperties {

    /** Master switch. When false, no rate-limit filter is registered (and Redis is not needed). */
    private boolean enabled = true;

    /** Max requests allowed per {@link #refillPeriod} per client IP. */
    private long capacity = 10;

    /** Window over which the full {@link #capacity} is refilled. */
    private Duration refillPeriod = Duration.ofMinutes(1);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getCapacity() {
        return capacity;
    }

    public void setCapacity(long capacity) {
        this.capacity = capacity;
    }

    public Duration getRefillPeriod() {
        return refillPeriod;
    }

    public void setRefillPeriod(Duration refillPeriod) {
        this.refillPeriod = refillPeriod;
    }
}
