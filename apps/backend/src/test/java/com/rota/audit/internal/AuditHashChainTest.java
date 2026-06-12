package com.rota.audit.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.rota.common.tenant.TenantContext;
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

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Audit hash-chain: signup writes a verifiable, append-only, secret-free chain")
class AuditHashChainTest {

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

    private ObjectMapper canonical;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("chain integrity + secret exclusion + append-only enforcement")
    void signupProducesVerifiableHashChain() throws Exception {
        canonical = objectMapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

        String body = """
                {"email":"audit@example.com","password":"sup3r-secret-pw",
                 "displayName":"Audit User","organizationName":"Audit Org"}
                """;
        String response = mockMvc.perform(post("/api/v1/auth/register").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID tenantId = UUID.fromString(objectMapper.readTree(response).get("tenantId").asText());

        TenantContext.setTenantId(tenantId);
        List<Row> rows = readChain(tenantId);

        // Signup writes: 1 tenant + 4 roles + 1 user = 6 create events (all in one transaction).
        assertThat(rows).hasSize(6);
        assertThat(rows).extracting(r -> r.entityType + "." + r.action)
                .contains("tenant.create", "role.create", "user.create");

        // (1) Each row's stored record_hash actually covers its content + its prev_hash.
        for (Row r : rows) {
            String recomputed = HashChain.compute(r.tenantId, r.actorUserId, r.actorIp, r.occurredAt,
                    r.entityType, r.entityId, r.action, canonical(r.beforeState), canonical(r.afterState),
                    r.correlationId, r.prevHash);
            assertThat(recomputed).as("record_hash for %s.%s", r.entityType, r.action).isEqualTo(r.recordHash);
        }

        // (2) Chain linkage: exactly one genesis (null prev_hash); every other prev_hash points at a
        //     real prior record_hash; all record_hashes are unique.
        Set<String> hashes = new HashSet<>(rows.stream().map(r -> r.recordHash).toList());
        assertThat(hashes).hasSize(rows.size());
        long genesisCount = rows.stream().filter(r -> r.prevHash == null).count();
        assertThat(genesisCount).isEqualTo(1);
        for (Row r : rows) {
            if (r.prevHash != null) {
                assertThat(hashes).as("prev_hash links to a real record").contains(r.prevHash);
            }
        }

        // (3) Secrets are never captured: the user snapshot has email but no password hash.
        Row userCreate = rows.stream()
                .filter(r -> "user".equals(r.entityType) && "create".equals(r.action)).findFirst().orElseThrow();
        Map<String, Object> userState = objectMapper.readValue(userCreate.afterState, Map.class);
        assertThat(userState).containsKey("email").doesNotContainKey("passwordHash");

        // (4) Append-only: UPDATE and DELETE are silently discarded by the DB rules.
        Row first = rows.get(0);
        assertThat(jdbc.update("UPDATE audit.events SET action = 'tampered' WHERE id = ?", first.id)).isZero();
        assertThat(jdbc.update("DELETE FROM audit.events WHERE id = ?", first.id)).isZero();
        String stillThere = jdbc.queryForObject(
                "SELECT action FROM audit.events WHERE id = ?", String.class, first.id);
        assertThat(stillThere).isEqualTo(first.action);
    }

    /** Re-canonicalise stored jsonb (which loses key order and adds spaces) the way the writer hashed it. */
    private String canonical(String json) throws Exception {
        if (json == null) {
            return null;
        }
        return canonical.writeValueAsString(objectMapper.readValue(json, Map.class));
    }

    private List<Row> readChain(UUID tenantId) {
        return jdbc.query("""
                        SELECT id, tenant_id, actor_user_id, host(actor_ip) AS actor_ip, occurred_at,
                               entity_type, entity_id, action, before_state::text AS before_state,
                               after_state::text AS after_state, correlation_id, prev_hash, record_hash
                        FROM audit.events WHERE tenant_id = ? ORDER BY seq
                        """,
                (rs, n) -> new Row(
                        rs.getObject("id", UUID.class),
                        rs.getObject("tenant_id", UUID.class),
                        rs.getObject("actor_user_id", UUID.class),
                        rs.getString("actor_ip"),
                        rs.getObject("occurred_at", OffsetDateTime.class),
                        rs.getString("entity_type"),
                        rs.getObject("entity_id", UUID.class),
                        rs.getString("action"),
                        rs.getString("before_state"),
                        rs.getString("after_state"),
                        rs.getObject("correlation_id", UUID.class),
                        rs.getString("prev_hash"),
                        rs.getString("record_hash")),
                tenantId);
    }

    private record Row(UUID id, UUID tenantId, UUID actorUserId, String actorIp, OffsetDateTime occurredAt,
                       String entityType, UUID entityId, String action, String beforeState, String afterState,
                       UUID correlationId, String prevHash, String recordHash) {
    }
}
