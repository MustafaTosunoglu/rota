package com.rota.iam.web;

import java.util.List;
import java.util.UUID;

/** Identity of the currently authenticated user. */
public record MeResponse(UUID userId, String tenantId, String email, String displayName, List<String> roles) {
}
