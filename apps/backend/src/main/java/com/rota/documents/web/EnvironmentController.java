package com.rota.documents.web;

import com.rota.documents.internal.EnvironmentService;
import com.rota.documents.web.DocumentDtos.CreateEnvironmentRequest;
import com.rota.documents.web.DocumentDtos.EnvironmentResponse;
import com.rota.documents.web.DocumentDtos.UpdateEnvironmentRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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

/** Per-version environment (base URL) management. */
@RestController
@RequestMapping("/api/v1")
public class EnvironmentController {

    private final EnvironmentService environmentService;

    public EnvironmentController(EnvironmentService environmentService) {
        this.environmentService = environmentService;
    }

    @GetMapping("/versions/{versionId}/environments")
    @PreAuthorize("hasRole('viewer')")
    public List<EnvironmentResponse> listEnvironments(@PathVariable UUID versionId) {
        return environmentService.list(versionId).stream().map(EnvironmentResponse::from).toList();
    }

    @PostMapping("/versions/{versionId}/environments")
    @PreAuthorize("hasRole('editor')")
    @ResponseStatus(HttpStatus.CREATED)
    public EnvironmentResponse createEnvironment(@PathVariable UUID versionId,
                                                 @Valid @RequestBody CreateEnvironmentRequest request) {
        return EnvironmentResponse.from(environmentService.create(
                versionId, request.name(), request.baseUrl(), request.productionWarn()));
    }

    @PatchMapping("/environments/{environmentId}")
    @PreAuthorize("hasRole('editor')")
    public EnvironmentResponse updateEnvironment(@PathVariable UUID environmentId,
                                                 @Valid @RequestBody UpdateEnvironmentRequest request) {
        return EnvironmentResponse.from(environmentService.update(
                environmentId, request.name(), request.baseUrl(), request.productionWarn()));
    }

    @DeleteMapping("/environments/{environmentId}")
    @PreAuthorize("hasRole('editor')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEnvironment(@PathVariable UUID environmentId) {
        environmentService.delete(environmentId);
    }
}
