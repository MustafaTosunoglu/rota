package com.rota.iam.internal;

import java.time.Duration;

/** The two single-use token flows backed by the {@code verification_tokens} table. */
public enum VerificationPurpose {

    EMAIL_VERIFY("email_verify", Duration.ofHours(24)),
    PASSWORD_RESET("password_reset", Duration.ofHours(1));

    private final String dbValue;
    private final Duration ttl;

    VerificationPurpose(String dbValue, Duration ttl) {
        this.dbValue = dbValue;
        this.ttl = ttl;
    }

    /** Value stored in the {@code purpose} column (matches the table's CHECK constraint). */
    public String dbValue() {
        return dbValue;
    }

    public Duration ttl() {
        return ttl;
    }
}
