/**
 * Public API of the iam module (e.g. {@link com.rota.iam.api.UserRegisteredEvent}), exposed
 * to other modules as the {@code api} named interface so future listeners (audit, etc.) can
 * reference its events without a boundary violation.
 */
@org.springframework.modulith.NamedInterface("api")
package com.rota.iam.api;
