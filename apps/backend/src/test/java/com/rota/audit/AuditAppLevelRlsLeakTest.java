package com.rota.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rota.common.tenant.TenantContext;
import com.rota.iam.jpa.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * App-level (repository + audit) RLS isolation, complementing the raw-JDBC RlsCrossTenantLeakTest:
 * the unprivileged rota_app role, scoped only by {@code TenantContext}, can never see another
 * tenant's audit rows or users.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("App-level RLS: tenants cannot see each other's audit rows or users")
class AuditAppLevelRlsLeakTest {

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
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("audit rows and users are visible only within their own tenant context")
    void tenantsAreIsolatedAtTheAppLayer() throws Exception {
        UUID tenantA = register("alice@example.com", "Alice Org");
        UUID tenantB = register("bob@example.com", "Bob Org");

        // Each tenant context sees ONLY its own audit rows (6 create events from signup).
        TenantContext.setTenantId(tenantA);
        assertThat(countAuditRows()).isEqualTo(6);
        assertThat(countAuditRowsFor(tenantB)).isZero();          // B's rows invisible to A
        assertThat(userRepository.findByEmail("bob@example.com")).isEmpty();

        TenantContext.setTenantId(tenantB);
        assertThat(countAuditRows()).isEqualTo(6);
        assertThat(countAuditRowsFor(tenantA)).isZero();          // A's rows invisible to B
        assertThat(userRepository.findByEmail("bob@example.com")).isPresent();
        assertThat(userRepository.findByEmail("alice@example.com")).isEmpty();
    }

    /** All audit rows visible in the current tenant context (RLS-scoped). */
    private int countAuditRows() {
        return jdbc.queryForObject("SELECT count(*) FROM audit.events", Integer.class);
    }

    /** Rows that explicitly belong to another tenant — RLS makes this 0 from outside that tenant. */
    private int countAuditRowsFor(UUID tenantId) {
        return jdbc.queryForObject("SELECT count(*) FROM audit.events WHERE tenant_id = ?", Integer.class, tenantId);
    }

    private UUID register(String email, String org) throws Exception {
        String body = """
                {"email":"%s","password":"sup3r-secret-pw","displayName":"U","organizationName":"%s"}
                """.formatted(email, org);
        String response = mockMvc.perform(post("/api/v1/auth/register").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("tenantId").asText());
    }
}
