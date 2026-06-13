package com.rota.endpoints.api;

import com.rota.endpoints.api.ExportModel.ContentExport;

import java.util.UUID;

/** Cross-module port exposing a version's full content for exporters. RLS-scoped. */
public interface ContentExportReader {

    ContentExport read(UUID versionId);
}
