package com.rota.endpoints.api;

import java.util.UUID;

/** Cross-module port: resolve an endpoint's routing data for the proxy (RLS-scoped). */
public interface EndpointResolver {

    /** @throws EndpointNotFoundException if absent in the current tenant */
    EndpointTarget resolve(UUID endpointId);

    record EndpointTarget(UUID endpointId, UUID documentVersionId, String method, String path) {
    }
}
