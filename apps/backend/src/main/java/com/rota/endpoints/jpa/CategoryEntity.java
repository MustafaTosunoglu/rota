package com.rota.endpoints.jpa;

import com.rota.audit.api.AuditEntityListener;
import com.rota.audit.api.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.util.UUID;

/** Maps the {@code categories} table: groups endpoints inside a document version. */
@Entity
@Table(name = "categories")
@EntityListeners(AuditEntityListener.class)
@Auditable(type = "category", fields = {"name", "sortOrder"})
public class CategoryEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "document_version_id", nullable = false, updatable = false)
    private UUID documentVersionId;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
