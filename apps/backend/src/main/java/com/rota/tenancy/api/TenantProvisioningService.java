package com.rota.tenancy.api;

import java.util.UUID;

/**
 * Public tenancy API for creating tenants. Lives in {@code tenancy.api} so other modules
 * (e.g. iam during signup) can create a tenant WITHOUT reaching into tenancy internals —
 * preserving Spring Modulith boundaries.
 */
public interface TenantProvisioningService {

    /**
     * Create a new tenant with the given id, slug and name, provisioning a wrapped
     * per-tenant DEK in the same row.
     *
     * <p>The caller MUST already have {@link com.rota.common.tenant.TenantContext} bound to
     * {@code tenantId} (the tenants RLS {@code WITH CHECK} compares the row id to the GUC),
     * and MUST run inside a transaction.
     */
    void createTenant(UUID tenantId, String slug, String name);
}
