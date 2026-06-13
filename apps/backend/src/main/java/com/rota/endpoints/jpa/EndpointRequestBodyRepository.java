package com.rota.endpoints.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EndpointRequestBodyRepository extends JpaRepository<EndpointRequestBodyEntity, UUID> {

    List<EndpointRequestBodyEntity> findAllByEndpointIdOrderByContentType(UUID endpointId);

    List<EndpointRequestBodyEntity> findAllByEndpointId(UUID endpointId);
}
