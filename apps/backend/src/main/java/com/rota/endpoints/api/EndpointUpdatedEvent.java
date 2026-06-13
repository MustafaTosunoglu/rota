package com.rota.endpoints.api;

import java.util.UUID;

public record EndpointUpdatedEvent(UUID tenantId, UUID documentVersionId, UUID endpointId) {
}
