package com.rota.endpoints.api;

import com.rota.endpoints.api.ImportModel.DedupMode;
import com.rota.endpoints.api.ImportModel.ImportCategory;
import com.rota.endpoints.api.ImportModel.ImportEndpoint;
import com.rota.endpoints.api.ImportModel.ImportResult;

import java.util.List;
import java.util.UUID;

/** Cross-module port that materialises parsed content into a draft version. */
public interface ContentImporter {

    /**
     * Creates the referenced categories (find-or-create by name) and the endpoints with their
     * sub-resources in the given draft version. Endpoints whose (method, path) already exist
     * are handled per {@code mode}. The version must be an editable draft.
     */
    ImportResult importInto(UUID versionId, List<ImportCategory> categories,
                            List<ImportEndpoint> endpoints, DedupMode mode);
}
