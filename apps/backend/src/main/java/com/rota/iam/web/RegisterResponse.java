package com.rota.iam.web;

import java.util.UUID;

/** Signup response: the newly created tenant and owner-user ids. No tokens yet (login is separate). */
public record RegisterResponse(UUID tenantId, UUID userId) {
}
