/**
 * Public API of the tenancy module — exposed to other modules as the {@code api} named
 * interface. Without this, Spring Modulith treats the sub-package as module-internal and
 * forbids cross-module access (only the module's base package is exposed by default).
 */
@org.springframework.modulith.NamedInterface("api")
package com.rota.tenancy.api;
