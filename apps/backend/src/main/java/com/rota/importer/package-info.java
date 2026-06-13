/**
 * Import (OpenAPI 3.x / Postman v2.1 / cURL) and OpenAPI export (plan §13.6, Phase 4).
 * Parsers produce a neutral model and the module materialises it through {@code documents.api}
 * and {@code endpoints.api} write ports — it never touches another module's internals.
 */
@ApplicationModule(displayName = "Importer")
package com.rota.importer;

import org.springframework.modulith.ApplicationModule;
