package com.rota.iam.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Body for {@code POST /api/v1/auth/forgot-password}. */
public record ForgotPasswordRequest(@NotBlank @Email String email) {
}
