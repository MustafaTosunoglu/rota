package com.rota.iam.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Signup request body for {@code POST /api/v1/auth/register}. */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 10, max = 200) String password,
        @NotBlank @Size(max = 120) String displayName,
        @NotBlank @Size(max = 120) String organizationName,
        @Size(max = 10) String locale) {
}
