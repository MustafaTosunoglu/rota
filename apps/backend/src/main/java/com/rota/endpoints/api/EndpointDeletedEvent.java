package com.rota.endpoints.api;

import java.util.UUID;

public record EndpointDeletedEvent(UUID tenantId, UUID documentVersionId, UUID endpointId) {
}
