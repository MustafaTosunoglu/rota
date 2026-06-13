package com.rota.documents.internal;

import com.rota.common.security.CurrentUser;
import com.rota.common.tenant.TenantContext;
import com.rota.documents.api.DocumentNotFoundException;
import com.rota.documents.jpa.DocumentEntity;
import com.rota.documents.jpa.DocumentRepository;
import com.rota.documents.jpa.DocumentVersionEntity;
import com.rota.documents.jpa.DocumentVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Document CRUD. Every query is already tenant-scoped by RLS; the service only adds
 * tenant_id on INSERT (RLS WITH CHECK) and business validation on top.
 */
@Service
public class DocumentService {

    private final DocumentRepository documents;
    private final DocumentVersionRepository versions;

    public DocumentService(DocumentRepository documents, DocumentVersionRepository versions) {
        this.documents = documents;
        this.versions = versions;
    }

    public record CreateDocumentCommand(String name, String slug, String description,
                                        String visibility, String initialVersionLabel) {
    }

    public record UpdateDocumentCommand(String name, String slug, String description,
                                        String visibility, Map<String, Object> branding) {
    }

    /** Creates the document together with its first draft version. */
    @Transactional
    public DocumentEntity create(CreateDocumentCommand command) {
        String slug = command.slug() != null && !command.slug().isBlank()
                ? slugify(command.slug())
                : slugify(command.name());
        if (documents.existsBySlug(slug)) {
            // Friendly pre-check; the (tenant_id, slug) unique constraint remains the authority.
            throw new SlugAlreadyInUseException(slug);
        }

        DocumentEntity document = new DocumentEntity();
        document.setTenantId(TenantContext.getTenantId());
        document.setSlug(slug);
        document.setName(command.name().trim());
        document.setDescription(command.description());
        if (command.visibility() != null) {
            document.setVisibility(command.visibility());
        }
        document.setCreatedBy(CurrentUser.requireId());
        document = documents.save(document);

        DocumentVersionEntity initial = new DocumentVersionEntity();
        initial.setTenantId(document.getTenantId());
        initial.setDocumentId(document.getId());
        initial.setVersionLabel(command.initialVersionLabel() != null
                && !command.initialVersionLabel().isBlank() ? command.initialVersionLabel().trim() : "v1");
        versions.save(initial);

        return document;
    }

    @Transactional(readOnly = true)
    public List<DocumentEntity> list() {
        return documents.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public DocumentEntity get(UUID documentId) {
        return documents.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
    }

    @Transactional
    public DocumentEntity update(UUID documentId, UpdateDocumentCommand command) {
        DocumentEntity document = get(documentId);
        if (command.name() != null && !command.name().isBlank()) {
            document.setName(command.name().trim());
        }
        if (command.slug() != null && !command.slug().isBlank()) {
            String slug = slugify(command.slug());
            if (!slug.equals(document.getSlug()) && documents.existsBySlug(slug)) {
                throw new SlugAlreadyInUseException(slug);
            }
            document.setSlug(slug);
        }
        if (command.description() != null) {
            document.setDescription(command.description());
        }
        if (command.visibility() != null) {
            document.setVisibility(command.visibility());
        }
        if (command.branding() != null) {
            document.setBranding(command.branding());
        }
        return document;
    }

    @Transactional
    public void delete(UUID documentId) {
        DocumentEntity document = get(documentId);
        // current_version_id references a version row; clear it so the version cascade
        // (document delete → versions delete) is not blocked by the back-reference.
        document.setCurrentVersionId(null);
        documents.flush();
        documents.delete(document);
    }

    /** Lowercases, strips accents and collapses everything non-alphanumeric to single dashes. */
    static String slugify(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return normalized.isBlank() ? "doc" : normalized;
    }
}
