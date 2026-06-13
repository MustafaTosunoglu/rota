package com.rota.proxy.internal;

import java.util.UUID;

/** Per-tenant daily Try It quota (Free tier). */
public interface TryItQuota {

    /** Records one use; throws {@link QuotaExceededException} if today's limit is exceeded. */
    void consume(UUID tenantId);

    /** Remaining uses for the tenant today (>= 0). */
    long remaining(UUID tenantId);
}
