package com.rota.consumers.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConsumerGroupMemberRepository extends JpaRepository<ConsumerGroupMemberEntity, UUID> {

    List<ConsumerGroupMemberEntity> findAllByGroupIdOrderByEmail(UUID groupId);

    Optional<ConsumerGroupMemberEntity> findByGroupIdAndEmail(UUID groupId, String email);

    Optional<ConsumerGroupMemberEntity> findByTokenHash(String tokenHash);
}
