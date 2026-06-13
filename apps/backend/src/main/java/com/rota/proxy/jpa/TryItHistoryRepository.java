package com.rota.proxy.jpa;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TryItHistoryRepository extends JpaRepository<TryItHistoryEntity, UUID> {

    List<TryItHistoryEntity> findAllByEndpointIdOrderByExecutedAtDesc(UUID endpointId, Limit limit);
}
