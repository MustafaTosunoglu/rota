package com.rota.importer.internal;

import com.rota.documents.api.EnvironmentSpec;
import com.rota.endpoints.api.ImportModel.ImportCategory;
import com.rota.endpoints.api.ImportModel.ImportEndpoint;

import java.util.List;

/**
 * Neutral parse result, shown to the user for preview and sent back to apply. Composed of the
 * target modules' api DTOs so no mapping/duplication is needed at apply time.
 */
public record ParsedImport(
        String suggestedTitle,
        List<EnvironmentSpec> environments,
        List<ImportCategory> categories,
        List<ImportEndpoint> endpoints) {
}
