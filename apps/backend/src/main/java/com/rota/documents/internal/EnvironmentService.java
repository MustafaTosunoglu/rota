package com.rota.documents.internal;

import com.rota.common.tenant.TenantContext;
import com.rota.documents.api.DocumentVersionGuard;
import com.rota.documents.jpa.EnvironmentEntity;
import com.rota.documents.jpa.EnvironmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Per-version environments. Mutations follow the same draft-only rule as version content —
 * a published version is an immutable snapshot, base URLs included (review note: if live
 * URL changes become a real need, a targeted exception can relax this later).
 */
@Service
public class EnvironmentService {

    private final EnvironmentRepository environments;
    private final DocumentVersionGuard versionGuard;

    public EnvironmentService(EnvironmentRepository environments, DocumentVersionGuard versionGuard) {
        this.environments = environments;
        this.versionGuard = versionGuard;
    }

    @Transactional(readOnly = true)
    public List<EnvironmentEntity> list(UUID versionId) {
        versionGuard.requireVersion(versionId);
        return environments.findAllByDocumentVersionIdOrderByName(versionId);
    }

    @Transactional
    public EnvironmentEntity create(UUID versionId, String name, String baseUrl, boolean productionWarn) {
        versionGuard.requireEditable(versionId);
        EnvironmentEntity environment = new EnvironmentEntity();
        environment.setTenantId(TenantContext.getTenantId());
        environment.setDocumentVersionId(versionId);
        environment.setName(name.trim());
        environment.setBaseUrl(baseUrl.trim());
        environment.setProductionWarn(productionWarn);
        return environments.save(environment);
    }

    @Transactional
    public EnvironmentEntity update(UUID environmentId, String name, String baseUrl, Boolean productionWarn) {
        EnvironmentEntity environment = get(environmentId);
        versionGuard.requireEditable(environment.getDocumentVersionId());
        if (name != null && !name.isBlank()) {
            environment.setName(name.trim());
        }
        if (baseUrl != null && !baseUrl.isBlank()) {
            environment.setBaseUrl(baseUrl.trim());
        }
        if (productionWarn != null) {
            environment.setProductionWarn(productionWarn);
        }
        return environment;
    }

    @Transactional
    public void delete(UUID environmentId) {
        EnvironmentEntity environment = get(environmentId);
        versionGuard.requireEditable(environment.getDocumentVersionId());
        environments.delete(environment);
    }

    private EnvironmentEntity get(UUID environmentId) {
        return environments.findById(environmentId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Environment not found"));
    }
}
