/**
 * Server-side "Try It" proxy (plan §13.1 Mode A, Phase 5). Executes a user's request against
 * a documented endpoint's environment with SSRF protection, timeouts, response-size limits,
 * sensitive-header redaction and a Free-tier daily quota. Reads endpoint/environment routing
 * data through {@code endpoints.api} / {@code documents.api} ports.
 */
@ApplicationModule(displayName = "Proxy")
package com.rota.proxy;

import org.springframework.modulith.ApplicationModule;
