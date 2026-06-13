package com.rota.documents.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EnvironmentRepository extends JpaRepository<EnvironmentEntity, UUID> {

    List<EnvironmentEntity> findAllByDocumentVersionIdOrderByName(UUID documentVersionId);

    List<EnvironmentEntity> findAllByDocumentVersionId(UUID documentVersionId);
}
