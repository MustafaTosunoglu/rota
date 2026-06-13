package com.rota.importer.web;

import com.rota.importer.internal.ImportFormat;
import com.rota.importer.internal.ImportParseException;
import com.rota.importer.internal.ImportService;
import com.rota.importer.internal.ParsedImport;
import com.rota.importer.web.ImportDtos.ApplyRequest;
import com.rota.importer.web.ImportDtos.ApplyResult;
import com.rota.importer.web.ImportDtos.ParseRequest;
import jakarta.validation.Valid;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Import API (plan tasks 4.1–4.6). Two steps: {@code /import/parse} returns a neutral preview
 * model (no writes), then {@code /versions/{id}/import} persists it into the draft version.
 * Both require editor (writing intent); the version-editable check happens at apply time.
 */
@RestController
@RequestMapping("/api/v1")
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @PostMapping("/import/parse")
    @PreAuthorize("hasRole('editor')")
    public ParsedImport parse(@Valid @RequestBody ParseRequest request) {
        return importService.parse(toFormat(request.format()), request.content());
    }

    @PostMapping("/versions/{versionId}/import")
    @PreAuthorize("hasRole('editor')")
    public ApplyResult apply(@PathVariable UUID versionId, @Valid @RequestBody ApplyRequest request) {
        return ApplyResult.from(importService.apply(versionId, request.parsed(), request.dedupMode()));
    }

    private ImportFormat toFormat(String format) {
        try {
            return ImportFormat.valueOf(format.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ImportParseException("Unsupported import format: " + format);
        }
    }
}
