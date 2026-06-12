package com.rota.tenancy.jpa;

import com.rota.audit.api.AuditEntityListener;
import com.rota.audit.api.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Maps the {@code tenants} table (internal to the tenancy module).
 *
 * <p>The id is assigned in the application (not DB-generated) so that a tenant can be
 * created with its {@link com.rota.common.tenant.TenantContext} already set to that id —
 * required because the RLS {@code WITH CHECK} on this table compares the row id to the
 * current tenant GUC.
 */
@Entity
@Table(name = "tenants")
@EntityListeners(AuditEntityListener.class)
@Auditable(type = "tenant", fields = {"slug", "name", "plan"})
public class TenantEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String plan = "free";

    @Column(name = "encrypted_dek")
    private byte[] encryptedDek;

    @Column(name = "suspended_at")
    private OffsetDateTime suspendedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

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

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPlan() {
        return plan;
    }

    public void setPlan(String plan) {
        this.plan = plan;
    }

    public byte[] getEncryptedDek() {
        return encryptedDek;
    }

    public void setEncryptedDek(byte[] encryptedDek) {
        this.encryptedDek = encryptedDek;
    }

    public OffsetDateTime getSuspendedAt() {
        return suspendedAt;
    }

    public void setSuspendedAt(OffsetDateTime suspendedAt) {
        this.suspendedAt = suspendedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
