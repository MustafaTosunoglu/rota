package com.rota.iam.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    /** Revoke every still-active refresh token for a user (used on password reset). */
    @Modifying
    @Query("update RefreshTokenEntity t set t.revokedAt = :now "
            + "where t.userId = :userId and t.revokedAt is null")
    int revokeAllActiveForUser(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);
}
