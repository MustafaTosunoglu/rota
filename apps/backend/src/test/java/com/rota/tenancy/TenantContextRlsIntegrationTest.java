package com.rota.tenancy;

import com.rota.common.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runtime proof of task 1.6: when the application connects as the unprivileged
 * {@code rota_app} role through {@link com.rota.common.tenant.TenantAwareDataSource},
 * ordinary {@link JdbcTemplate} queries are automatically scoped to whatever
 * {@link TenantContext} holds — i.e. RLS is enforced for the real application datasource,
 * not just in a hand-rolled JDBC test.
 *
 * <p>Flyway runs as the container superuser (privileged, creates {@code rota_app}); the
 * runtime datasource connects as {@code rota_app}.
 */
@SpringBootTest
@Testcontainers
@DisplayName("Runtime RLS via TenantContext + rota_app datasource")
class TenantContextRlsIntegrationTest {

    private static final String ROTA_APP_PASSWORD = "pw_" + UUID.randomUUID().toString().replace("-", "");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        // Flyway = privileged container superuser; it creates rota_app with our password.
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.flyway.placeholders.rotaAppPassword", () -> ROTA_APP_PASSWORD);
        registry.add("spring.flyway.placeholders.rotaAdminPassword", () -> ROTA_APP_PASSWORD);
        // Runtime datasource = unprivileged rota_app (so RLS actually applies).
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "rota_app");
        registry.add("spring.datasource.password", () -> ROTA_APP_PASSWORD);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("JdbcTemplate queries are scoped to the bound tenant; none when unbound")
    void runtimeQueriesAreTenantScoped() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        // Seed each tenant while its own context is bound (RLS WITH CHECK requires
        // the GUC to match the row being inserted).
        TenantContext.setTenantId(tenantA);
        jdbc.update("INSERT INTO tenants (id, slug, name) VALUES (?, ?, ?)", tenantA, "rls-a", "Tenant A");
        jdbc.update("INSERT INTO users (tenant_id, email) VALUES (?, ?)", tenantA, "a@rls.test");

        TenantContext.setTenantId(tenantB);
        jdbc.update("INSERT INTO tenants (id, slug, name) VALUES (?, ?, ?)", tenantB, "rls-b", "Tenant B");
        jdbc.update("INSERT INTO users (tenant_id, email) VALUES (?, ?)", tenantB, "b@rls.test");

        // Bound to A → only A's user is visible.
        TenantContext.setTenantId(tenantA);
        List<String> visibleAsA = jdbc.queryForList("SELECT email FROM users", String.class);
        assertThat(visibleAsA).containsExactly("a@rls.test");

        // Bound to B → only B's user.
        TenantContext.setTenantId(tenantB);
        List<String> visibleAsB = jdbc.queryForList("SELECT email FROM users", String.class);
        assertThat(visibleAsB).containsExactly("b@rls.test");

        // Unbound → fail closed, zero rows.
        TenantContext.clear();
        Integer count = jdbc.queryForObject("SELECT count(*) FROM users", Integer.class);
        assertThat(count).isZero();
    }
}
