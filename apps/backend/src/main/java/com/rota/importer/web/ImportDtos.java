package com.rota.importer.web;

import com.rota.endpoints.api.ImportModel.DedupMode;
import com.rota.endpoints.api.ImportModel.ImportResult;
import com.rota.importer.internal.ParsedImport;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Request/response records of the importer module's REST API. */
public final class ImportDtos {

    private ImportDtos() {
    }

    /** {@code format} is one of openapi | postman | curl (case-insensitive). */
    public record ParseRequest(@NotBlank String format, @NotBlank String content) {
    }

    public record ApplyRequest(@NotNull ParsedImport parsed, @NotNull DedupMode dedupMode) {
    }

    public record ApplyResult(int created, int overwritten, int skipped) {

        public static ApplyResult from(ImportResult result) {
            return new ApplyResult(result.created(), result.overwritten(), result.skipped());
        }
    }
}
