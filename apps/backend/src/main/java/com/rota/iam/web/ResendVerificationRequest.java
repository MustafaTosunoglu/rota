package com.rota.iam.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Body for {@code POST /api/v1/auth/verify-email/resend}. */
public record ResendVerificationRequest(@NotBlank @Email String email) {
}
