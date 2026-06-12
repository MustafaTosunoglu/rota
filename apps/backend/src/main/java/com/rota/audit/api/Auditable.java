package com.rota.audit.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a JPA entity for automatic audit capture. Pair with
 * {@code @EntityListeners(AuditEntityListener.class)}.
 *
 * <p>{@link #fields()} is a strict ALLOW-LIST: only these fields are snapshotted into the audit
 * trail. Anything not listed (password hashes, encryption keys, MFA secrets, …) is never
 * captured — secrets must not reach the audit log.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /** Logical entity type recorded in {@code audit.events.entity_type} (e.g. "user"). */
    String type();

    /** Allow-listed field names captured into before/after state. */
    String[] fields();
}
