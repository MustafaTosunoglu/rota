package com.rota.iam;

import com.rota.common.tenant.TenantContext;
import com.rota.iam.jpa.UserEntity;
import com.rota.iam.jpa.UserRepository;
import com.rota.tenancy.jpa.TenantEntity;
import com.rota.tenancy.jpa.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the JPA entities + Spring Data repositories end-to-end against a real Postgres:
 * confirms Hibernate {@code ddl-auto: validate} accepts the mappings (context boots), and
 * that ordinary repository calls are tenant-scoped by RLS via {@link TenantContext}.
 */
@SpringBootTest
@Testcontainers
@DisplayName("iam/tenancy repositories under RLS + Hibernate validate")
class UserRepositoryRlsTest {

    private static final String ROTA_APP_PASSWORD = "pw_" + UUID.randomUUID().toString().replace("-", "");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.flyway.placeholders.rotaAppPassword", () -> ROTA_APP_PASSWORD);
        registry.add("spring.flyway.placeholders.rotaAdminPassword", () -> ROTA_APP_PASSWORD);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "rota_app");
        registry.add("spring.datasource.password", () -> ROTA_APP_PASSWORD);
    }

    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void repositoryReadsAreTenantScoped() {
        UUID tenantA = createTenant("repo-a", "Repo A");
        createUser(tenantA, "a@repo.test");

        UUID tenantB = createTenant("repo-b", "Repo B");
        createUser(tenantB, "b@repo.test");

        // As tenant A: only A's user is reachable, B's is hidden by RLS.
        TenantContext.setTenantId(tenantA);
        assertThat(userRepository.findAll()).extracting(UserEntity::getEmail).containsExactly("a@repo.test");
        assertThat(userRepository.findByEmail("a@repo.test")).isPresent();
        assertThat(userRepository.findByEmail("b@repo.test")).isEmpty();

        // As tenant B: the mirror image.
        TenantContext.setTenantId(tenantB);
        assertThat(userRepository.findAll()).extracting(UserEntity::getEmail).containsExactly("b@repo.test");

        // No context: nothing visible (fail closed).
        TenantContext.clear();
        assertThat(userRepository.findAll()).isEmpty();
    }

    private UUID createTenant(String slug, String name) {
        UUID id = UUID.randomUUID();
        // Bind context to the new tenant id first: RLS WITH CHECK on tenants requires it.
        TenantContext.setTenantId(id);
        TenantEntity tenant = new TenantEntity();
        tenant.setId(id);
        tenant.setSlug(slug);
        tenant.setName(name);
        tenant.setPlan("free");
        tenantRepository.save(tenant);
        return id;
    }

    private void createUser(UUID tenantId, String email) {
        TenantContext.setTenantId(tenantId);
        UserEntity user = new UserEntity();
        user.setTenantId(tenantId);
        user.setEmail(email);
        userRepository.save(user);
    }
}
