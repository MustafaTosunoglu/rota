package com.rota.common.tenant;

import java.util.Optional;
import java.util.UUID;

/**
 * Holds the current tenant id for the executing thread.
 *
 * <p>Implemented as a static {@link ThreadLocal} holder rather than a request-scoped
 * Spring bean (a deliberate deviation from plan §8.1) for two reasons:
 * <ul>
 *   <li>The connection hook ({@link TenantAwareDataSource}) runs on whatever thread is
 *       borrowing the JDBC connection; a {@code ThreadLocal} is reachable there without
 *       a live web-request scope.</li>
 *   <li>It works uniformly on web threads and non-request worker threads (proxy/loadtest/
 *       background profiles), and is virtual-thread safe — each (virtual) thread carries
 *       its own value.</li>
 * </ul>
 *
 * <p>The value is set by the JWT authentication filter at the start of each request
 * (Phase 1D) and MUST be cleared when the request/unit of work ends to avoid leaking a
 * tenant id onto a pooled platform thread.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    /** Bind the given tenant id to the current thread. */
    public static void setTenantId(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    /** @return the current tenant id, or {@code null} if none is bound. */
    public static UUID getTenantId() {
        return CURRENT_TENANT.get();
    }

    /** @return the current tenant id wrapped in an {@link Optional}. */
    public static Optional<UUID> currentTenantId() {
        return Optional.ofNullable(CURRENT_TENANT.get());
    }

    /** Remove any tenant binding from the current thread. */
    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
