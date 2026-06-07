package com.rota.iam.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body for {@code POST /api/v1/auth/reset-password}. Password rules mirror signup. */
public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 10, max = 200) String newPassword) {
}
