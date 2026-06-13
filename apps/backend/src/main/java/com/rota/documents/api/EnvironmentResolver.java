package com.rota.documents.api;

import java.util.UUID;

/** Cross-module port: resolve an environment's base URL for the proxy (RLS-scoped). */
public interface EnvironmentResolver {

    /** @throws DocumentVersionNotFoundException if absent in the current tenant */
    EnvironmentTarget resolve(UUID environmentId);

    record EnvironmentTarget(UUID environmentId, UUID documentVersionId, String baseUrl,
                             boolean productionWarn) {
    }
}
