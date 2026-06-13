package com.rota.documents.api;

import java.util.List;

/** Document + version metadata needed to build an export (e.g. OpenAPI), read RLS-scoped. */
public record DocumentExport(
        String name,
        String description,
        String versionLabel,
        List<EnvironmentSpec> environments) {
}
