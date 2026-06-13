package com.rota.endpoints.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EndpointResponseRepository extends JpaRepository<EndpointResponseEntity, UUID> {

    List<EndpointResponseEntity> findAllByEndpointIdOrderByStatusCode(UUID endpointId);

    List<EndpointResponseEntity> findAllByEndpointId(UUID endpointId);
}
