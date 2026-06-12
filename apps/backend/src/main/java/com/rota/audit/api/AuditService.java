package com.rota.audit.api;

/**
 * Records audit events into the append-only, hash-chained {@code audit.events} log. Writes are
 * buffered and flushed (in order) just before the surrounding transaction commits, so the
 * per-tenant hash chain stays consistent.
 */
public interface AuditService {

    void record(AuditEvent event);
}
