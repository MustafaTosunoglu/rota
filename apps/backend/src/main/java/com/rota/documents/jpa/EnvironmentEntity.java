package com.rota.documents.jpa;

import com.rota.audit.api.AuditEntityListener;
import com.rota.audit.api.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.util.UUID;

/** Maps the {@code environments} table: per-version base URLs (test/staging/prod). */
@Entity
@Table(name = "environments")
@EntityListeners(AuditEntityListener.class)
@Auditable(type = "environment", fields = {"name", "baseUrl"})
public class EnvironmentEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "document_version_id", nullable = false, updatable = false)
    private UUID documentVersionId;

    @Column(nullable = false)
    private String name;

    @Column(name = "base_url", nullable = false)
    private String baseUrl;

    @Column(name = "is_production_warn", nullable = false)
    private boolean productionWarn;

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

    public UUID getDocumentVersionId() {
        return documentVersionId;
    }

    public void setDocumentVersionId(UUID documentVersionId) {
        this.documentVersionId = documentVersionId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public boolean isProductionWarn() {
        return productionWarn;
    }

    public void setProductionWarn(boolean productionWarn) {
        this.productionWarn = productionWarn;
    }
}
