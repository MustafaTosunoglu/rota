package com.rota.endpoints.internal;

import com.rota.common.tenant.TenantContext;
import com.rota.documents.api.DocumentVersionGuard;
import com.rota.endpoints.jpa.CategoryEntity;
import com.rota.endpoints.jpa.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Category CRUD inside a document version (draft-only mutations via the version guard). */
@Service
public class CategoryService {

    private final CategoryRepository categories;
    private final DocumentVersionGuard versionGuard;

    public CategoryService(CategoryRepository categories, DocumentVersionGuard versionGuard) {
        this.categories = categories;
        this.versionGuard = versionGuard;
    }

    @Transactional(readOnly = true)
    public List<CategoryEntity> list(UUID versionId) {
        versionGuard.requireVersion(versionId);
        return categories.findAllByDocumentVersionIdOrderBySortOrderAscNameAsc(versionId);
    }

    @Transactional
    public CategoryEntity create(UUID versionId, String name, String description, Integer sortOrder) {
        versionGuard.requireEditable(versionId);
        CategoryEntity category = new CategoryEntity();
        category.setTenantId(TenantContext.getTenantId());
        category.setDocumentVersionId(versionId);
        category.setName(name.trim());
        category.setDescription(description);
        category.setSortOrder(sortOrder != null ? sortOrder : 0);
        return categories.save(category);
    }

    @Transactional
    public CategoryEntity update(UUID categoryId, String name, String description, Integer sortOrder) {
        CategoryEntity category = get(categoryId);
        versionGuard.requireEditable(category.getDocumentVersionId());
        if (name != null && !name.isBlank()) {
            category.setName(name.trim());
        }
        if (description != null) {
            category.setDescription(description);
        }
        if (sortOrder != null) {
            category.setSortOrder(sortOrder);
        }
        return category;
    }

    /** Endpoints in the category survive: the FK is ON DELETE SET NULL (uncategorized). */
    @Transactional
    public void delete(UUID categoryId) {
        CategoryEntity category = get(categoryId);
        versionGuard.requireEditable(category.getDocumentVersionId());
        categories.delete(category);
    }

    private CategoryEntity get(UUID categoryId) {
        return categories.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Category", categoryId));
    }
}
