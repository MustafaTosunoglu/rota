package com.rota.endpoints.jpa;

import com.rota.audit.api.AuditEntityListener;
import com.rota.audit.api.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

/** Maps the {@code endpoint_responses} table (documented responses per status code). */
@Entity
@Table(name = "endpoint_responses")
@EntityListeners(AuditEntityListener.class)
@Auditable(type = "endpoint_response", fields = {"statusCode", "contentType"})
public class EndpointResponseEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "endpoint_id", nullable = false, updatable = false)
    private UUID endpointId;

    @Column(name = "status_code", nullable = false)
    private int statusCode;

    @Column
    private String description;

    @Column(name = "content_type", nullable = false)
    private String contentType = "application/json";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "schema_json")
    private Map<String, Object> schemaJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "example_json")
    private Map<String, Object> exampleJson;

    @PrePersist
    void assignId() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getEndpointId() {
        return endpointId;
    }

    public void setEndpointId(UUID endpointId) {
        this.endpointId = endpointId;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Map<String, Object> getSchemaJson() {
        return schemaJson;
    }

    public void setSchemaJson(Map<String, Object> schemaJson) {
        this.schemaJson = schemaJson;
    }

    public Map<String, Object> getExampleJson() {
        return exampleJson;
    }

    public void setExampleJson(Map<String, Object> exampleJson) {
        this.exampleJson = exampleJson;
    }
}
