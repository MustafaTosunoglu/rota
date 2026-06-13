package com.rota.endpoints.internal;

import com.rota.endpoints.api.EndpointGuard;
import com.rota.endpoints.api.EndpointNotFoundException;
import com.rota.endpoints.jpa.EndpointRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** {@link EndpointGuard} backed by the (RLS-scoped) endpoint repository. */
@Component
class EndpointGuardImpl implements EndpointGuard {

    private final EndpointRepository endpoints;

    EndpointGuardImpl(EndpointRepository endpoints) {
        this.endpoints = endpoints;
    }

    @Override
    @Transactional(readOnly = true)
    public void requireEndpoint(UUID endpointId) {
        if (!endpoints.existsById(endpointId)) {
            throw new EndpointNotFoundException(endpointId);
        }
    }
}
