package com.rota.endpoints.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EndpointRepository extends JpaRepository<EndpointEntity, UUID> {

    List<EndpointEntity> findAllByDocumentVersionIdOrderBySortOrderAscPathAsc(UUID documentVersionId);

    List<EndpointEntity> findAllByDocumentVersionId(UUID documentVersionId);

    boolean existsByDocumentVersionIdAndMethodAndPath(UUID documentVersionId, String method, String path);
}
