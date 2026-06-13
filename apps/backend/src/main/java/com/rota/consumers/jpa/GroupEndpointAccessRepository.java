package com.rota.consumers.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupEndpointAccessRepository extends JpaRepository<GroupEndpointAccessEntity, UUID> {

    List<GroupEndpointAccessEntity> findAllByGroupId(UUID groupId);

    Optional<GroupEndpointAccessEntity> findByGroupIdAndEndpointId(UUID groupId, UUID endpointId);
}
