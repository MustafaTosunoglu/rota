package com.rota.endpoints.api;

import java.util.UUID;

public record EndpointCreatedEvent(UUID tenantId, UUID documentVersionId, UUID endpointId) {
}
