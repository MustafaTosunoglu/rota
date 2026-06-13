package com.rota.documents.web;

import com.rota.documents.internal.VersionService;
import com.rota.documents.web.DocumentDtos.CreateVersionRequest;
import com.rota.documents.web.DocumentDtos.UpdateVersionRequest;
import com.rota.documents.web.DocumentDtos.VersionResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Version lifecycle API: list/create under a document, mutate/publish/archive by version id. */
@RestController
@RequestMapping("/api/v1")
public class DocumentVersionController {

    private final VersionService versionService;

    public DocumentVersionController(VersionService versionService) {
        this.versionService = versionService;
    }

    @GetMapping("/documents/{documentId}/versions")
    @PreAuthorize("hasRole('viewer')")
    public List<VersionResponse> listVersions(@PathVariable UUID documentId) {
        return versionService.list(documentId).stream().map(VersionResponse::from).toList();
    }

    @PostMapping("/documents/{documentId}/versions")
    @PreAuthorize("hasRole('editor')")
    @ResponseStatus(HttpStatus.CREATED)
    public VersionResponse createVersion(@PathVariable UUID documentId,
                                         @Valid @RequestBody CreateVersionRequest request) {
        return VersionResponse.from(versionService.create(
                documentId, request.versionLabel(), request.cloneFromVersionId()));
    }

    @GetMapping("/versions/{versionId}")
    @PreAuthorize("hasRole('viewer')")
    public VersionResponse getVersion(@PathVariable UUID versionId) {
        return VersionResponse.from(versionService.get(versionId));
    }

    @PatchMapping("/versions/{versionId}")
    @PreAuthorize("hasRole('editor')")
    public VersionResponse updateVersion(@PathVariable UUID versionId,
                                         @Valid @RequestBody UpdateVersionRequest request) {
        return VersionResponse.from(versionService.updateMeta(
                versionId, request.versionLabel(), request.changelogMd()));
    }

    @PostMapping("/versions/{versionId}/publish")
    @PreAuthorize("hasRole('editor')")
    public VersionResponse publishVersion(@PathVariable UUID versionId) {
        return VersionResponse.from(versionService.publish(versionId));
    }

    @PostMapping("/versions/{versionId}/archive")
    @PreAuthorize("hasRole('editor')")
    public VersionResponse archiveVersion(@PathVariable UUID versionId) {
        return VersionResponse.from(versionService.archive(versionId));
    }
}
