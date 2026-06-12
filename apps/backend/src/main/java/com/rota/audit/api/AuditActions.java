package com.rota.audit.api;

/** Canonical {@code action} values stored in {@code audit.events.action}. */
public final class AuditActions {

    // Entity lifecycle (captured automatically by AuditEntityListener).
    public static final String CREATE = "create";
    public static final String UPDATE = "update";
    public static final String DELETE = "delete";

    // Security events (recorded explicitly by the iam module).
    public static final String LOGIN = "login";
    public static final String LOGIN_FAILED = "login_failed";
    public static final String LOGOUT = "logout";
    public static final String PASSWORD_RESET = "password_reset";
    public static final String EMAIL_VERIFIED = "email_verified";

    private AuditActions() {
    }
}
