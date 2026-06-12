/**
 * Public surface of the audit module: the {@link com.rota.audit.api.AuditService} domain code
 * calls to record events, the {@link com.rota.audit.api.Auditable} marker + entity listener that
 * capture entity CUD automatically, and the {@link com.rota.audit.api.AuditEvent} carrier.
 */
@org.springframework.modulith.NamedInterface("api")
package com.rota.audit.api;
