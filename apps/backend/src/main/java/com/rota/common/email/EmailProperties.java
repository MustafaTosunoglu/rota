package com.rota.common.email;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Outbound email settings: the From address and the frontend base URL used to build links. */
@Component
@ConfigurationProperties("rota.email")
public class EmailProperties {

    /** From address for transactional emails. */
    private String from = "no-reply@rota.local";

    /** Frontend base URL; verification / reset links are built relative to it. */
    private String appBaseUrl = "http://localhost:5173";

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
