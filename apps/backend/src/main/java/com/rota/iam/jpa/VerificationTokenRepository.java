package com.rota.iam.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VerificationTokenRepository extends JpaRepository<VerificationTokenEntity, UUID> {

    Optional<VerificationTokenEntity> findByTokenHash(String tokenHash);
}
