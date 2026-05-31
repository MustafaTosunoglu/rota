package com.rota.iam.api;

import java.util.UUID;

/** Published when a new user (and their tenant, at signup) is registered. Plan §10. */
public record UserRegisteredEvent(UUID tenantId, UUID userId, String email) {
}
