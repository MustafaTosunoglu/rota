package com.rota.iam.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Stamp the last-login time without dirtying the managed entity — a bulk update bypasses the
     * audit entity listener, so routine logins don't churn the audit log (login is recorded as an
     * explicit security event instead).
     */
    @Modifying
    @Query("update UserEntity u set u.lastLoginAt = :now where u.id = :userId")
    void updateLastLogin(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);
}
