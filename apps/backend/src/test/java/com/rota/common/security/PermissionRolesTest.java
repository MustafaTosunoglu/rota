package com.rota.common.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rota.common.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 2 acceptance: "permission denied case'leri 403 dönüyor". Role floors via the
 * hierarchy (owner ⊃ admin ⊃ editor ⊃ viewer): reads need viewer, writes need editor,
 * document deletion needs admin.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("RBAC: viewer reads only, editor writes, only admin+ deletes documents")
class PermissionRolesTest {

    private static final String ROTA_APP_PASSWORD = "pw_" + UUID.randomUUID().toString().replace("-", "");
    private static final String PASSWORD = "sup3r-secret-pw";

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
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("role floors enforced across documents API")
    void roleFloors() throws Exception {
        // Owner via the normal signup; editor & viewer seeded directly with those roles.
        JsonNode reg = json(mockMvc.perform(post("/api/v1/auth/register").contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"rbac-owner@example.com","password":"%s",
                                 "displayName":"Owner","organizationName":"RBAC Org"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        UUID tenantId = UUID.fromString(reg.get("tenantId").asText());

        TenantContext.setTenantId(tenantId);
        jdbc.update("UPDATE users SET email_verified = true WHERE email = ?", "rbac-owner@example.com");
        seedUser(tenantId, "rbac-editor@example.com", "editor");
        seedUser(tenantId, "rbac-viewer@example.com", "viewer");
        TenantContext.clear();

        String ownerToken = login("rbac-owner@example.com");
        String editorToken = login("rbac-editor@example.com");
        String viewerToken = login("rbac-viewer@example.com");

        // Owner creates a document.
        JsonNode doc = json(mockMvc.perform(post("/api/v1/documents")
                        .header(AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"RBAC API\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String docId = doc.get("id").asText();
        String versionId = json(mockMvc.perform(get("/api/v1/documents/" + docId + "/versions")
                        .header(AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get(0).get("id").asText();

        // VIEWER: reads pass …
        mockMvc.perform(get("/api/v1/documents").header(AUTHORIZATION, "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("RBAC API"));
        mockMvc.perform(get("/api/v1/versions/" + versionId + "/endpoints")
                        .header(AUTHORIZATION, "Bearer " + viewerToken))
                .andExpect(status().isOk());
        // … every write is 403.
        mockMvc.perform(post("/api/v1/documents").header(AUTHORIZATION, "Bearer " + viewerToken)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"Nope\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/documents/" + docId).header(AUTHORIZATION, "Bearer " + viewerToken)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"Nope\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/versions/" + versionId + "/endpoints")
                        .header(AUTHORIZATION, "Bearer " + viewerToken)
                        .contentType(APPLICATION_JSON).content("{\"method\":\"GET\",\"path\":\"/x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/versions/" + versionId + "/publish")
                        .header(AUTHORIZATION, "Bearer " + viewerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/documents/" + docId).header(AUTHORIZATION, "Bearer " + viewerToken))
                .andExpect(status().isForbidden());

        // EDITOR: content writes pass, document deletion stays forbidden (admin floor).
        mockMvc.perform(post("/api/v1/versions/" + versionId + "/endpoints")
                        .header(AUTHORIZATION, "Bearer " + editorToken)
                        .contentType(APPLICATION_JSON).content("{\"method\":\"GET\",\"path\":\"/ok\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(delete("/api/v1/documents/" + docId).header(AUTHORIZATION, "Bearer " + editorToken))
                .andExpect(status().isForbidden());

        // OWNER: passes the admin floor through the hierarchy.
        mockMvc.perform(delete("/api/v1/documents/" + docId).header(AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isNoContent());
    }

    /** Inserts a verified user with the given system role (RLS satisfied via TenantContext). */
    private void seedUser(UUID tenantId, String email, String roleName) {
        UUID userId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users (id, tenant_id, email, email_verified, password_hash, display_name)
                VALUES (?, ?, ?, true, ?, ?)
                """, userId, tenantId, email, passwordEncoder.encode(PASSWORD), roleName);
        jdbc.update("""
                INSERT INTO user_roles (user_id, role_id, tenant_id)
                SELECT ?, id, ? FROM roles WHERE name = ?
                """, userId, tenantId, roleName);
    }

    private String login(String email) throws Exception {
        JsonNode tokens = json(mockMvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return tokens.get("accessToken").asText();
    }

    private JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }
}
