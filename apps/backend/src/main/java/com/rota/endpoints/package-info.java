/**
 * Version content: categories, endpoints and their parameters / request bodies / responses
 * (plan §10, Phase 2). Depends on {@code documents.api} for version validation (the guard);
 * the reverse direction is event-only ({@code DocumentVersionClonedEvent}) to avoid a cycle.
 */
@ApplicationModule(displayName = "Endpoints")
package com.rota.endpoints;

import org.springframework.modulith.ApplicationModule;
