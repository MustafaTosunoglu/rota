/**
 * Consumer groups, email-based invitations and per-document / per-endpoint access grants
 * (plan §10, Phase 2E). Uses {@code documents.api} / {@code endpoints.api} guards to
 * validate grant targets; nothing depends on this module yet (the public consumer-facing
 * doc view arrives in later phases).
 */
@ApplicationModule(displayName = "Consumers")
package com.rota.consumers;

import org.springframework.modulith.ApplicationModule;
