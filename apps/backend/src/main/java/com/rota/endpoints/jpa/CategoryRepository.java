package com.rota.endpoints.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<CategoryEntity, UUID> {

    List<CategoryEntity> findAllByDocumentVersionIdOrderBySortOrderAscNameAsc(UUID documentVersionId);

    List<CategoryEntity> findAllByDocumentVersionId(UUID documentVersionId);
}
