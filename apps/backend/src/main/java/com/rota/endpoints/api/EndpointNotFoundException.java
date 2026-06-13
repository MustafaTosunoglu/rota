package com.rota.endpoints.api;

import java.util.UUID;

public class EndpointNotFoundException extends RuntimeException {

    public EndpointNotFoundException(UUID endpointId) {
        super("Endpoint not found: " + endpointId);
    }
}
