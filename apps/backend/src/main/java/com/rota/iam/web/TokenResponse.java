package com.rota.iam.web;

/** Access + refresh token pair returned by login/refresh. */
public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {
}
