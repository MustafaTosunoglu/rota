package com.rota.consumers.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupDocumentAccessRepository extends JpaRepository<GroupDocumentAccessEntity, UUID> {

    List<GroupDocumentAccessEntity> findAllByGroupId(UUID groupId);

    Optional<GroupDocumentAccessEntity> findByGroupIdAndDocumentId(UUID groupId, UUID documentId);
}
