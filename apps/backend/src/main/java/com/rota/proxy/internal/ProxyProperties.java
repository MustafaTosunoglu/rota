package com.rota.proxy.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Try It proxy settings (plan §13.1 security rules). */
@Component
@ConfigurationProperties("rota.proxy")
public class ProxyProperties {

    /** Block private/loopback/link-local target IPs (SSRF). Disable only for local dev/tests. */
    private boolean blockPrivateNetworks = true;

    /** Per-request connection timeout. */
    private Duration connectTimeout = Duration.ofSeconds(10);

    /** Per-request total timeout. */
    private Duration totalTimeout = Duration.ofSeconds(30);

    /** Max response bytes read (10MB). Larger responses are truncated. */
    private long maxResponseBytes = 10L * 1024 * 1024;

    /** Free-tier daily Try It limit per tenant. */
    private int dailyFreeLimit = 100;

    /** Whether the daily quota is enforced (needs Redis). Off in tests. */
    private boolean dailyLimitEnabled = true;

    public boolean isBlockPrivateNetworks() {
        return blockPrivateNetworks;
    }

    public void setBlockPrivateNetworks(boolean blockPrivateNetworks) {
        this.blockPrivateNetworks = blockPrivateNetworks;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getTotalTimeout() {
        return totalTimeout;
    }

    public void setTotalTimeout(Duration totalTimeout) {
        this.totalTimeout = totalTimeout;
    }

    public long getMaxResponseBytes() {
        return maxResponseBytes;
    }

    public void setMaxResponseBytes(long maxResponseBytes) {
        this.maxResponseBytes = maxResponseBytes;
    }

    public int getDailyFreeLimit() {
        return dailyFreeLimit;
    }

    public void setDailyFreeLimit(int dailyFreeLimit) {
        this.dailyFreeLimit = dailyFreeLimit;
    }

    public boolean isDailyLimitEnabled() {
        return dailyLimitEnabled;
    }

    public void setDailyLimitEnabled(boolean dailyLimitEnabled) {
        this.dailyLimitEnabled = dailyLimitEnabled;
    }
}
