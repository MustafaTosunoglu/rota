package com.rota.iam.web;

import jakarta.validation.constraints.NotBlank;

/** Body for {@code POST /api/v1/auth/verify-email}. */
public record VerifyEmailRequest(@NotBlank String token) {
}
