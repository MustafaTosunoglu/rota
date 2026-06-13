package com.rota.endpoints.api;

import java.util.UUID;

/** Lets other modules (consumers) assert an endpoint exists in the current tenant. */
public interface EndpointGuard {

    /** @throws EndpointNotFoundException when absent (or RLS-hidden, which looks the same) */
    void requireEndpoint(UUID endpointId);
}
