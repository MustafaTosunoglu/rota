package com.rota.importer.internal;

import com.rota.documents.api.EnvironmentSpec;
import com.rota.documents.api.EnvironmentWriter;
import com.rota.endpoints.api.ContentImporter;
import com.rota.endpoints.api.ImportModel.DedupMode;
import com.rota.endpoints.api.ImportModel.ImportResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Orchestrates import: parse (stateless, for preview) and apply (persist into a draft version
 * through the cross-module write ports). Apply runs in one transaction so a partial import
 * never lands.
 */
@Service
public class ImportService {

    private final OpenApiParser openApiParser;
    private final PostmanParser postmanParser;
    private final CurlParser curlParser;
    private final EnvironmentWriter environmentWriter;
    private final ContentImporter contentImporter;

    public ImportService(OpenApiParser openApiParser,
                         PostmanParser postmanParser,
                         CurlParser curlParser,
                         EnvironmentWriter environmentWriter,
                         ContentImporter contentImporter) {
        this.openApiParser = openApiParser;
        this.postmanParser = postmanParser;
        this.curlParser = curlParser;
        this.environmentWriter = environmentWriter;
        this.contentImporter = contentImporter;
    }

    /** Stateless parse for the preview step. */
    public ParsedImport parse(ImportFormat format, String content) {
        if (content == null || content.isBlank()) {
            throw new ImportParseException("No content to import.");
        }
        return switch (format) {
            case OPENAPI -> openApiParser.parse(content);
            case POSTMAN -> postmanParser.parse(content);
            case CURL -> curlParser.parse(content);
        };
    }

    /** Persists a (possibly user-edited) parse result into the target draft version. */
    @Transactional
    public ImportResult apply(UUID versionId, ParsedImport parsed, DedupMode mode) {
        if (parsed.environments() != null) {
            for (EnvironmentSpec environment : parsed.environments()) {
                environmentWriter.addIfAbsent(versionId, environment);
            }
        }
        return contentImporter.importInto(versionId,
                parsed.categories() != null ? parsed.categories() : java.util.List.of(),
                parsed.endpoints() != null ? parsed.endpoints() : java.util.List.of(),
                mode);
    }
}
