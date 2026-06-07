package com.rota.iam.internal;

import com.rota.common.config.JwtProperties;
import com.rota.iam.jpa.RefreshTokenEntity;
import com.rota.iam.jpa.RefreshTokenRepository;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Issues RS256 access tokens and opaque refresh tokens.
 *
 * <p>Access token claims: {@code sub}=userId, {@code tenant_id}, {@code roles}, plus standard
 * iss/iat/exp. Refresh token: {@code {tenantId}.{random-secret}} handed to the client; only its
 * SHA-256 hash is persisted (rotated on use).
 */
@Service
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenService(JwtEncoder jwtEncoder,
                        JwtProperties jwtProperties,
                        RefreshTokenRepository refreshTokenRepository) {
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    public String issueAccessToken(UUID userId, UUID tenantId, Collection<String> roles) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .id(UUID.randomUUID().toString()) // jti — enables per-token revocation (blacklist)
                .issuer(jwtProperties.getIssuer())
                .issuedAt(now)
                .expiresAt(now.plus(jwtProperties.getAccessTokenTtl()))
                .subject(userId.toString())
                .claim("tenant_id", tenantId.toString())
                .claim("roles", List.copyOf(roles))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long accessTokenTtlSeconds() {
        return jwtProperties.getAccessTokenTtl().toSeconds();
    }

    /** Create, persist (hashed) and return a new refresh token for the user. */
    public IssuedRefreshToken issueRefreshToken(UUID userId, UUID tenantId) {
        String rawValue = tenantId + "." + randomSecret();
        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.setTenantId(tenantId);
        entity.setUserId(userId);
        entity.setTokenHash(hash(rawValue));
        entity.setExpiresAt(OffsetDateTime.now().plus(jwtProperties.getRefreshTokenTtl()));
        RefreshTokenEntity saved = refreshTokenRepository.save(entity);
        return new IssuedRefreshToken(rawValue, saved.getId());
    }

    /** SHA-256 hex of a raw refresh token (what we store / look up by). */
    public String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String randomSecret() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record IssuedRefreshToken(String rawValue, UUID id) {
    }
}
