package com.rota.documents.web;

import com.rota.documents.jpa.DocumentEntity;
import com.rota.documents.jpa.DocumentVersionEntity;
import com.rota.documents.jpa.EnvironmentEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/** Request/response records of the documents module's REST API. */
public final class DocumentDtos {

    private DocumentDtos() {
    }

    private static final String VISIBILITY_PATTERN = "public|unlisted|private";

    public record CreateDocumentRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 200) String slug,
            @Size(max = 5000) String description,
            @Pattern(regexp = VISIBILITY_PATTERN) String visibility,
            @Size(max = 50) String initialVersionLabel) {
    }

    public record UpdateDocumentRequest(
            @Size(max = 200) String name,
            @Size(max = 200) String slug,
            @Size(max = 5000) String description,
            @Pattern(regexp = VISIBILITY_PATTERN) String visibility,
            Map<String, Object> branding) {
    }

    public record DocumentResponse(UUID id, String slug, String name, String description,
                                   String visibility, UUID currentVersionId,
                                   Map<String, Object> branding, UUID createdBy,
                                   OffsetDateTime createdAt, OffsetDateTime updatedAt,
                                   OffsetDateTime publishedAt) {

        public static DocumentResponse from(DocumentEntity entity) {
            return new DocumentResponse(entity.getId(), entity.getSlug(), entity.getName(),
                    entity.getDescription(), entity.getVisibility(), entity.getCurrentVersionId(),
                    entity.getBranding(), entity.getCreatedBy(), entity.getCreatedAt(),
                    entity.getUpdatedAt(), entity.getPublishedAt());
        }
    }

    public record CreateVersionRequest(
            @NotBlank @Size(max = 50) String versionLabel,
            UUID cloneFromVersionId) {
    }

    public record UpdateVersionRequest(
            @Size(max = 50) String versionLabel,
            @Size(max = 50000) String changelogMd) {
    }

    public record VersionResponse(UUID id, UUID documentId, String versionLabel, String status,
                                  String changelogMd, OffsetDateTime createdAt,
                                  OffsetDateTime publishedAt) {

        public static VersionResponse from(DocumentVersionEntity entity) {
            return new VersionResponse(entity.getId(), entity.getDocumentId(),
                    entity.getVersionLabel(), entity.getStatus(), entity.getChangelogMd(),
                    entity.getCreatedAt(), entity.getPublishedAt());
        }
    }

    public record CreateEnvironmentRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 2000) @Pattern(regexp = "https?://.+",
                    message = "must be an http(s) URL") String baseUrl,
            boolean productionWarn) {
    }

    public record UpdateEnvironmentRequest(
            @Size(max = 100) String name,
            @Size(max = 2000) @Pattern(regexp = "https?://.+",
                    message = "must be an http(s) URL") String baseUrl,
            Boolean productionWarn) {
    }

    public record EnvironmentResponse(UUID id, UUID documentVersionId, String name,
                                      String baseUrl, boolean productionWarn) {

        public static EnvironmentResponse from(EnvironmentEntity entity) {
            return new EnvironmentResponse(entity.getId(), entity.getDocumentVersionId(),
                    entity.getName(), entity.getBaseUrl(), entity.isProductionWarn());
        }
    }
}
