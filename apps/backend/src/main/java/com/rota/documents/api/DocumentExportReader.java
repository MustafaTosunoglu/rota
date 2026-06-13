package com.rota.documents.api;

import java.util.UUID;

/** Cross-module port exposing document/version metadata for exporters. */
public interface DocumentExportReader {

    /** @throws DocumentVersionNotFoundException if the version is absent in the current tenant */
    DocumentExport read(UUID versionId);
}
