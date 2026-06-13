package com.rota.endpoints.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EndpointParameterRepository extends JpaRepository<EndpointParameterEntity, UUID> {

    List<EndpointParameterEntity> findAllByEndpointIdOrderBySortOrderAscNameAsc(UUID endpointId);

    List<EndpointParameterEntity> findAllByEndpointId(UUID endpointId);
}
