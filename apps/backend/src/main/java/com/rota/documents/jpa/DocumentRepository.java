package com.rota.documents.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** RLS scopes every query to the current tenant; no explicit tenant filter needed. */
public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {

    List<DocumentEntity> findAllByOrderByCreatedAtDesc();

    boolean existsBySlug(String slug);
}
