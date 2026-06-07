package com.rota.common.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/** Rejects access tokens whose {@code jti} has been revoked (logout). Wired in {@code JwtConfig}. */
public class BlacklistTokenValidator implements OAuth2TokenValidator<Jwt> {

    private final TokenBlacklist blacklist;

    public BlacklistTokenValidator(TokenBlacklist blacklist) {
        this.blacklist = blacklist;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (blacklist.isBlacklisted(token.getId())) {
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("token_revoked", "Access token has been revoked", null));
        }
        return OAuth2TokenValidatorResult.success();
    }
}
