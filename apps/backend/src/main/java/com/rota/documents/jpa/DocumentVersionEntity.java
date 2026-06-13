package com.rota.documents.jpa;

import com.rota.audit.api.AuditEntityListener;
import com.rota.audit.api.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/** Maps the {@code document_versions} table (draft → published → archived lifecycle). */
@Entity
@Table(name = "document_versions")
@EntityListeners(AuditEntityListener.class)
@Auditable(type = "document_version", fields = {"versionLabel", "status"})
public class DocumentVersionEntity {

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_PUBLISHED = "published";
    public static final String STATUS_ARCHIVED = "archived";

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Column(name = "version_label", nullable = false)
    private String versionLabel;

    @Column(nullable = false)
    private String status = STATUS_DRAFT;

    @Column(name = "changelog_md")
    private String changelogMd;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "auto_diff_json")
    private Map<String, Object> autoDiffJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @PrePersist
    void assignId() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    public boolean isDraft() {
        return STATUS_DRAFT.equals(status);
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

    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    public String getVersionLabel() {
        return versionLabel;
    }

    public void setVersionLabel(String versionLabel) {
        this.versionLabel = versionLabel;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getChangelogMd() {
        return changelogMd;
    }

    public void setChangelogMd(String changelogMd) {
        this.changelogMd = changelogMd;
    }

    public Map<String, Object> getAutoDiffJson() {
        return autoDiffJson;
    }

    public void setAutoDiffJson(Map<String, Object> autoDiffJson) {
        this.autoDiffJson = autoDiffJson;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(OffsetDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }
}
