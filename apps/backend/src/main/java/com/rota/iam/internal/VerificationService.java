package com.rota.iam.internal;

import com.rota.audit.api.AuditActions;
import com.rota.audit.api.AuditEvent;
import com.rota.audit.api.AuditService;
import com.rota.common.email.EmailProperties;
import com.rota.common.email.EmailSender;
import com.rota.common.tenant.TenantContext;
import com.rota.iam.jpa.RefreshTokenRepository;
import com.rota.iam.jpa.UserEntity;
import com.rota.iam.jpa.UserRepository;
import com.rota.iam.jpa.VerificationTokenEntity;
import com.rota.iam.jpa.VerificationTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

/**
 * Email verification + password reset. Tokens are opaque {@code {tenantId}.{secret}} values
 * (same shape as refresh tokens) stored only as a SHA-256 hash; the raw value travels solely
 * in the email link.
 *
 * <p><b>RLS scoping (no new privileged path):</b> consume endpoints parse the tenant from the
 * token prefix; the email-driven endpoints (resend / forgot-password) reuse the existing
 * {@code auth_lookup_user} SECURITY DEFINER function. {@link TenantContext} is always bound
 * BEFORE the transaction (the connection's GUC is taken at tx begin) and cleared after.
 */
@Service
public class VerificationService {

    private static final Logger log = LoggerFactory.getLogger(VerificationService.class);

    private final JdbcTemplate jdbcTemplate;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailSender emailSender;
    private final EmailProperties emailProperties;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public VerificationService(JdbcTemplate jdbcTemplate,
                               TokenService tokenService,
                               PasswordEncoder passwordEncoder,
                               UserRepository userRepository,
                               VerificationTokenRepository verificationTokenRepository,
                               RefreshTokenRepository refreshTokenRepository,
                               EmailSender emailSender,
                               EmailProperties emailProperties,
                               AuditService auditService,
                               PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.emailSender = emailSender;
        this.emailProperties = emailProperties;
        this.auditService = auditService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** Issue a verification token and email the link. Called after signup and on resend. */
    public void issueAndSendVerification(UUID tenantId, UUID userId, String email) {
        TenantContext.setTenantId(tenantId);
        try {
            String rawToken = transactionTemplate.execute(status ->
                    issueToken(tenantId, userId, VerificationPurpose.EMAIL_VERIFY));
            sendVerificationEmail(email, rawToken);
        } finally {
            TenantContext.clear();
        }
    }

    /** Consume an email-verification token and flip {@code email_verified} on the user. */
    public void verifyEmail(String rawToken) {
        UUID tenantId = parseTenantId(rawToken);
        TenantContext.setTenantId(tenantId);
        try {
            transactionTemplate.executeWithoutResult(status -> {
                VerificationTokenEntity token = consume(rawToken, VerificationPurpose.EMAIL_VERIFY);
                UserEntity user = userRepository.findById(token.getUserId())
                        .orElseThrow(InvalidVerificationTokenException::new);
                user.setEmailVerified(true);
                auditService.record(AuditEvent.security(
                        AuditActions.EMAIL_VERIFIED, tenantId, user.getId(), user.getId(), null));
            });
        } finally {
            TenantContext.clear();
        }
    }

    /** Re-send the verification email. Silent no-op if the email is unknown or already verified. */
    public void resendVerification(String email) {
        AuthUser user = lookup(email);
        if (user == null || user.emailVerified()) {
            return; // No account enumeration: respond identically whether or not it applies.
        }
        issueAndSendVerification(user.tenantId(), user.id(), email);
    }

    /** Begin password reset. Silent no-op if the email is unknown (no account enumeration). */
    public void requestPasswordReset(String email) {
        AuthUser user = lookup(email);
        if (user == null) {
            return;
        }
        UUID tenantId = user.tenantId();
        TenantContext.setTenantId(tenantId);
        try {
            String rawToken = transactionTemplate.execute(status ->
                    issueToken(tenantId, user.id(), VerificationPurpose.PASSWORD_RESET));
            sendPasswordResetEmail(email, rawToken);
        } finally {
            TenantContext.clear();
        }
    }

    /** Consume a reset token, set the new password, and revoke all of the user's refresh tokens. */
    public void resetPassword(String rawToken, String newRawPassword) {
        UUID tenantId = parseTenantId(rawToken);
        TenantContext.setTenantId(tenantId);
        try {
            transactionTemplate.executeWithoutResult(status -> {
                VerificationTokenEntity token = consume(rawToken, VerificationPurpose.PASSWORD_RESET);
                UserEntity user = userRepository.findById(token.getUserId())
                        .orElseThrow(InvalidVerificationTokenException::new);
                user.setPasswordHash(passwordEncoder.encode(newRawPassword));
                // Resetting a password invalidates every existing session.
                refreshTokenRepository.revokeAllActiveForUser(user.getId(), OffsetDateTime.now());
                auditService.record(AuditEvent.security(
                        AuditActions.PASSWORD_RESET, tenantId, user.getId(), user.getId(), null));
            });
        } finally {
            TenantContext.clear();
        }
    }

    // --- internals -----------------------------------------------------------------------

    private String issueToken(UUID tenantId, UUID userId, VerificationPurpose purpose) {
        String rawToken = tenantId + "." + randomSecret();
        VerificationTokenEntity entity = new VerificationTokenEntity();
        entity.setTenantId(tenantId);
        entity.setUserId(userId);
        entity.setPurpose(purpose.dbValue());
        entity.setTokenHash(tokenService.hash(rawToken));
        entity.setExpiresAt(OffsetDateTime.now().plus(purpose.ttl()));
        verificationTokenRepository.save(entity);
        return rawToken;
    }

    private VerificationTokenEntity consume(String rawToken, VerificationPurpose expected) {
        VerificationTokenEntity token = verificationTokenRepository
                .findByTokenHash(tokenService.hash(rawToken))
                .orElseThrow(InvalidVerificationTokenException::new);
        OffsetDateTime now = OffsetDateTime.now();
        if (!expected.dbValue().equals(token.getPurpose()) || !token.isUsable(now)) {
            throw new InvalidVerificationTokenException();
        }
        token.setConsumedAt(now);
        return token;
    }

    private AuthUser lookup(String email) {
        return jdbcTemplate.query(
                "SELECT id, tenant_id, email_verified FROM auth_lookup_user(?)",
                rs -> rs.next()
                        ? new AuthUser(rs.getObject("id", UUID.class),
                                       rs.getObject("tenant_id", UUID.class),
                                       rs.getBoolean("email_verified"))
                        : null,
                email);
    }

    private UUID parseTenantId(String rawToken) {
        int dot = rawToken == null ? -1 : rawToken.indexOf('.');
        if (dot <= 0) {
            throw new InvalidVerificationTokenException();
        }
        try {
            return UUID.fromString(rawToken.substring(0, dot));
        } catch (IllegalArgumentException e) {
            throw new InvalidVerificationTokenException();
        }
    }

    private String randomSecret() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void sendVerificationEmail(String email, String rawToken) {
        String link = emailProperties.getAppBaseUrl() + "/verify-email?token=" + rawToken;
        String body = """
                <p>Welcome to Rota. Please confirm your email address to activate your account.</p>
                <p><a href="%s">Verify my email</a></p>
                <p>This link expires in 24 hours.</p>
                """.formatted(link);
        emailSender.send(email, "Verify your Rota email", body);
        log.debug("Issued email-verification token for {}", email);
    }

    private void sendPasswordResetEmail(String email, String rawToken) {
        String link = emailProperties.getAppBaseUrl() + "/reset-password?token=" + rawToken;
        String body = """
                <p>We received a request to reset your Rota password.</p>
                <p><a href="%s">Choose a new password</a></p>
                <p>This link expires in 1 hour. If you did not request this, you can ignore this email.</p>
                """.formatted(link);
        emailSender.send(email, "Reset your Rota password", body);
        log.debug("Issued password-reset token for {}", email);
    }

    private record AuthUser(UUID id, UUID tenantId, boolean emailVerified) {
    }
}
