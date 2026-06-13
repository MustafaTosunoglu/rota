package com.rota.consumers.jpa;

import com.rota.audit.api.AuditEntityListener;
import com.rota.audit.api.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.util.UUID;

/** Maps {@code consumer_group_document_access}: what a group may do with a document. */
@Entity
@Table(name = "consumer_group_document_access")
@EntityListeners(AuditEntityListener.class)
@Auditable(type = "consumer_group_document_access", fields = {"canView", "canTry", "canLoadtest"})
public class GroupDocumentAccessEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "group_id", nullable = false, updatable = false)
    private UUID groupId;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Column(name = "can_view", nullable = false)
    private boolean canView = true;

    @Column(name = "can_try", nullable = false)
    private boolean canTry;

    @Column(name = "can_loadtest", nullable = false)
    private boolean canLoadtest;

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

    public UUID getGroupId() {
        return groupId;
    }

    public void setGroupId(UUID groupId) {
        this.groupId = groupId;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    public boolean isCanView() {
        return canView;
    }

    public void setCanView(boolean canView) {
        this.canView = canView;
    }

    public boolean isCanTry() {
        return canTry;
    }

    public void setCanTry(boolean canTry) {
        this.canTry = canTry;
    }

    public boolean isCanLoadtest() {
        return canLoadtest;
    }

    public void setCanLoadtest(boolean canLoadtest) {
        this.canLoadtest = canLoadtest;
    }
}
