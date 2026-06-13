package com.rota.importer.web;

import com.rota.importer.internal.OpenApiExporter;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** OpenAPI export (plan task 4.7). Read-only → viewer floor. */
@RestController
@RequestMapping("/api/v1")
public class ExportController {

    private final OpenApiExporter exporter;

    public ExportController(OpenApiExporter exporter) {
        this.exporter = exporter;
    }

    @GetMapping(value = "/versions/{versionId}/export/openapi", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('viewer')")
    public Map<String, Object> exportOpenApi(@PathVariable UUID versionId) {
        return exporter.export(versionId);
    }
}
