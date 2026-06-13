package com.rota.documents.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersionEntity, UUID> {

    List<DocumentVersionEntity> findAllByDocumentIdOrderByCreatedAtDesc(UUID documentId);

    Optional<DocumentVersionEntity> findByDocumentIdAndStatus(UUID documentId, String status);
}
