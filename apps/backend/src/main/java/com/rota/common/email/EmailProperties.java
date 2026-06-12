package com.rota.common.email;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Outbound email settings: provider selection, From address and frontend link base URL. */
@Component
@ConfigurationProperties("rota.email")
public class EmailProperties {

    /** Delivery provider: {@code smtp} (dev → maildev) or {@code resend} (prod HTTP API). */
    private String provider = "smtp";

    /** From address for transactional emails. */
    private String from = "no-reply@rota.local";

    /** Frontend base URL; verification / reset links are built relative to it. */
    private String appBaseUrl = "http://localhost:5173";

    /** Resend API key — secret; supplied via local config / env, never committed or logged. */
    private String resendApiKey = "";

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getResendApiKey() {
        return resendApiKey;
    }

    public void setResendApiKey(String resendApiKey) {
        this.resendApiKey = resendApiKey;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getAppBaseUrl() {
        return appBaseUrl;
    }

    public void setAppBaseUrl(String appBaseUrl) {
        this.appBaseUrl = appBaseUrl;
    }
}
