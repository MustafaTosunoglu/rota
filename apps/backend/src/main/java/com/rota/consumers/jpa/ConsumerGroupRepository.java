package com.rota.consumers.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConsumerGroupRepository extends JpaRepository<ConsumerGroupEntity, UUID> {

    List<ConsumerGroupEntity> findAllByOrderByName();

    boolean existsByName(String name);
}
