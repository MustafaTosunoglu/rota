package com.rota.iam.internal;

import com.rota.common.tenant.TenantContext;
import com.rota.iam.jpa.RefreshTokenEntity;
import com.rota.iam.jpa.RefreshTokenRepository;
import com.rota.iam.jpa.RoleEntity;
import com.rota.iam.jpa.RoleRepository;
import com.rota.iam.jpa.UserRoleEntity;
import com.rota.iam.jpa.UserRepository;
import com.rota.iam.jpa.UserRoleRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Login / refresh / logout. Mirrors the registration "context-before-transaction" pattern:
 * the tenant is discovered first (login: via the {@code auth_lookup_user} SECURITY DEFINER
 * function that bypasses RLS for an exact email; refresh: from the token's tenant prefix),
 * then {@link TenantContext} is bound BEFORE the transaction so all subsequent writes are
 * RLS-scoped.
 */
@Service
public class AuthService {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TransactionTemplate transactionTemplate;

    public AuthService(JdbcTemplate jdbcTemplate,
                       PasswordEncoder passwordEncoder,
                       TokenService tokenService,
                       UserRepository userRepository,
                       UserRoleRepository userRoleRepository,
                       RoleRepository roleRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public AuthTokens login(String email, String rawPassword) {
        AuthUser lookup = jdbcTemplate.query(
                "SELECT id, tenant_id, password_hash, email_verified FROM auth_lookup_user(?)",
                rs -> rs.next()
                        ? new AuthUser(rs.getObject("id", UUID.class),
                                       rs.getObject("tenant_id", UUID.class),
                                       rs.getString("password_hash"),
                                       rs.getBoolean("email_verified"))
                        : null,
                email);

        if (lookup == null || lookup.passwordHash() == null
                || !passwordEncoder.matches(rawPassword, lookup.passwordHash())) {
            throw new InvalidCredentialsException();
        }
        if (!lookup.emailVerified()) {
            throw new EmailNotVerifiedException();
        }

        UUID tenantId = lookup.tenantId();
        UUID userId = lookup.id();
        TenantContext.setTenantId(tenantId);
        try {
            return transactionTemplate.execute(status -> issueTokens(userId, tenantId, true));
        } finally {
            TenantContext.clear();
        }
    }

    public AuthTokens refresh(String rawRefreshToken) {
        UUID tenantId = parseTenantId(rawRefreshToken);
        TenantContext.setTenantId(tenantId);
        try {
            return transactionTemplate.execute(status -> {
                RefreshTokenEntity token = refreshTokenRepository
                        .findByTokenHash(tokenService.hash(rawRefreshToken))
                        .orElseThrow(InvalidRefreshTokenException::new);
                OffsetDateTime now = OffsetDateTime.now();
                if (!token.isActive(now)) {
                    throw new InvalidRefreshTokenException();
                }
                AuthTokens tokens = issueTokens(token.getUserId(), tenantId, false);
                // Rotate: revoke the used token and link it to its replacement.
                token.setRevokedAt(now);
                token.setReplacedBy(tokens.refreshTokenId());
                return tokens;
            });
        } finally {
            TenantContext.clear();
        }
    }

    /** Revoke a refresh token. Authenticated request → tenant context is already bound by the filter. */
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(tokenService.hash(rawRefreshToken))
                .ifPresent(token -> {
                    if (token.getRevokedAt() == null) {
                        token.setRevokedAt(OffsetDateTime.now());
                    }
                });
    }

    private AuthTokens issueTokens(UUID userId, UUID tenantId, boolean updateLastLogin) {
        List<String> roles = loadRoleNames(userId);
        String accessToken = tokenService.issueAccessToken(userId, tenantId, roles);
        TokenService.IssuedRefreshToken refresh = tokenService.issueRefreshToken(userId, tenantId);
        if (updateLastLogin) {
            userRepository.findById(userId).ifPresent(user -> user.setLastLoginAt(OffsetDateTime.now()));
        }
        return new AuthTokens(accessToken, refresh.rawValue(), refresh.id(), tokenService.accessTokenTtlSeconds());
    }

    private List<String> loadRoleNames(UUID userId) {
        List<UUID> roleIds = userRoleRepository.findByUserId(userId).stream()
                .map(UserRoleEntity::getRoleId)
                .toList();
        return roleRepository.findAllById(roleIds).stream()
                .map(RoleEntity::getName)
                .toList();
    }

    private UUID parseTenantId(String rawRefreshToken) {
        int dot = rawRefreshToken == null ? -1 : rawRefreshToken.indexOf('.');
        if (dot <= 0) {
            throw new InvalidRefreshTokenException();
        }
        try {
            return UUID.fromString(rawRefreshToken.substring(0, dot));
        } catch (IllegalArgumentException e) {
            throw new InvalidRefreshTokenException();
        }
    }

    public record AuthTokens(String accessToken, String refreshToken, UUID refreshTokenId, long expiresInSeconds) {
    }

    private record AuthUser(UUID id, UUID tenantId, String passwordHash, boolean emailVerified) {
    }
}
