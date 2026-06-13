package com.rota.proxy.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Maps {@code try_it_history}. NOT @Auditable: it is itself an activity log, and its summaries
 * already redact sensitive headers before persistence. Append-only — persisted via
 * {@code EntityManager.persist} (TryItHistoryWriter), so the table grants no UPDATE.
 */
@Entity
@Table(name = "try_it_history")
public class TryItHistoryEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "endpoint_id", nullable = false, updatable = false)
    private UUID endpointId;

    @Column(name = "executed_at", nullable = false)
    private OffsetDateTime executedAt = OffsetDateTime.now();

    @Column(nullable = false)
    private String method;

    @Column(nullable = false)
    private String url;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_summary_json")
    private Map<String, Object> requestSummaryJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_summary_json")
    private Map<String, Object> responseSummaryJson;

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getEndpointId() {
        return endpointId;
    }

    public void setEndpointId(UUID endpointId) {
        this.endpointId = endpointId;
    }

    public OffsetDateTime getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(OffsetDateTime executedAt) {
        this.executedAt = executedAt;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public Map<String, Object> getRequestSummaryJson() {
        return requestSummaryJson;
    }

    public void setRequestSummaryJson(Map<String, Object> requestSummaryJson) {
        this.requestSummaryJson = requestSummaryJson;
    }

    public Map<String, Object> getResponseSummaryJson() {
        return responseSummaryJson;
    }

    public void setResponseSummaryJson(Map<String, Object> responseSummaryJson) {
        this.responseSummaryJson = responseSummaryJson;
    }
}
