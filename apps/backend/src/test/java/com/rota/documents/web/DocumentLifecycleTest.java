package com.rota.documents.web;

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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Documents: CRUD + draft → published → archived lifecycle (Phase 2 acceptance)")
class DocumentLifecycleTest {

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

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("create → version → publish → clone → archive → delete, with conflict rules")
    void documentLifecycle() throws Exception {
        String token = registerVerifyLogin("doc-owner@example.com", "Docs Org");

        // Create: slug derives from the name, an initial draft version comes along.
        JsonNode doc = postJson(token, "/api/v1/documents", """
                {"name":"Payment API","description":"Pay things","visibility":"private"}
                """, 201);
        UUID docId = UUID.fromString(doc.get("id").asText());
        assertThat(doc.get("slug").asText()).isEqualTo("payment-api");
        assertThat(doc.get("currentVersionId").isNull()).isTrue();

        JsonNode versions = getJson(token, "/api/v1/documents/" + docId + "/versions", 200);
        assertThat(versions).hasSize(1);
        UUID v1 = UUID.fromString(versions.get(0).get("id").asText());
        assertThat(versions.get(0).get("versionLabel").asText()).isEqualTo("v1");
        assertThat(versions.get(0).get("status").asText()).isEqualTo("draft");

        // Same name → same slug → 409.
        postJson(token, "/api/v1/documents", """
                {"name":"Payment API"}
                """, 409);

        // Update metadata.
        JsonNode updated = patchJson(token, "/api/v1/documents/" + docId, """
                {"name":"Payments API","visibility":"public","branding":{"primaryColor":"#093C5D"}}
                """, 200);
        assertThat(updated.get("name").asText()).isEqualTo("Payments API");
        assertThat(updated.get("branding").get("primaryColor").asText()).isEqualTo("#093C5D");

        // Environment on the draft version.
        postJson(token, "/api/v1/versions/" + v1 + "/environments", """
                {"name":"prod","baseUrl":"https://api.payments.example","productionWarn":true}
                """, 201);

        // Duplicate version label → 409.
        postJson(token, "/api/v1/documents/" + docId + "/versions", """
                {"versionLabel":"v1"}
                """, 409);

        // Publish v1: status flips, document points at it.
        JsonNode published = postJson(token, "/api/v1/versions/" + v1 + "/publish", null, 200);
        assertThat(published.get("status").asText()).isEqualTo("published");
        assertThat(published.get("publishedAt").isNull()).isFalse();
        JsonNode docAfterPublish = getJson(token, "/api/v1/documents/" + docId, 200);
        assertThat(docAfterPublish.get("currentVersionId").asText()).isEqualTo(v1.toString());
        assertThat(docAfterPublish.get("publishedAt").isNull()).isFalse();

        // Published versions are immutable: metadata and environments both refuse.
        patchJson(token, "/api/v1/versions/" + v1, """
                {"changelogMd":"too late"}
                """, 409);
        postJson(token, "/api/v1/versions/" + v1 + "/environments", """
                {"name":"staging","baseUrl":"https://staging.example"}
                """, 409);

        // Clone v1 → v2 (draft) including environments.
        JsonNode v2node = postJson(token, "/api/v1/documents/" + docId + "/versions", """
                {"versionLabel":"v2","cloneFromVersionId":"%s"}
                """.formatted(v1), 201);
        UUID v2 = UUID.fromString(v2node.get("id").asText());
        assertThat(v2node.get("status").asText()).isEqualTo("draft");
        JsonNode clonedEnvs = getJson(token, "/api/v1/versions/" + v2 + "/environments", 200);
        assertThat(clonedEnvs).hasSize(1);
        assertThat(clonedEnvs.get(0).get("name").asText()).isEqualTo("prod");

        // Draft v2 IS editable.
        patchJson(token, "/api/v1/versions/" + v2, """
                {"changelogMd":"## v2\\n- cloned"}
                """, 200);

        // Publishing v2 archives v1 and repoints the document.
        postJson(token, "/api/v1/versions/" + v2 + "/publish", null, 200);
        assertThat(getJson(token, "/api/v1/versions/" + v1, 200).get("status").asText())
                .isEqualTo("archived");
        assertThat(getJson(token, "/api/v1/documents/" + docId, 200)
                .get("currentVersionId").asText()).isEqualTo(v2.toString());

        // Archiving the live version clears current_version_id.
        postJson(token, "/api/v1/versions/" + v2 + "/archive", null, 200);
        assertThat(getJson(token, "/api/v1/documents/" + docId, 200)
                .get("currentVersionId").isNull()).isTrue();

        // Delete a document whose current version is still live (covers the FK unlink).
        JsonNode doc2 = postJson(token, "/api/v1/documents", """
                {"name":"Throwaway API"}
                """, 201);
        UUID doc2Id = UUID.fromString(doc2.get("id").asText());
        UUID doc2v1 = UUID.fromString(getJson(token, "/api/v1/documents/" + doc2Id + "/versions", 200)
                .get(0).get("id").asText());
        postJson(token, "/api/v1/versions/" + doc2v1 + "/publish", null, 200);
        mockMvc.perform(delete("/api/v1/documents/" + doc2Id).header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
        getJson(token, "/api/v1/documents/" + doc2Id, 404);

        // Unauthenticated access is rejected outright.
        mockMvc.perform(get("/api/v1/documents")).andExpect(status().isUnauthorized());
    }

    // --- helpers ---------------------------------------------------------------------

    private String registerVerifyLogin(String email, String org) throws Exception {
        JsonNode reg = json(mockMvc.perform(post("/api/v1/auth/register").contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"U","organizationName":"%s"}
                                """.formatted(email, PASSWORD, org)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        UUID tenantId = UUID.fromString(reg.get("tenantId").asText());
        TenantContext.setTenantId(tenantId);
        jdbc.update("UPDATE users SET email_verified = true WHERE email = ?", email);
        TenantContext.clear();
        JsonNode tokens = json(mockMvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return tokens.get("accessToken").asText();
    }

    private JsonNode postJson(String token, String url, String body, int expectedStatus) throws Exception {
        var request = post(url).header(AUTHORIZATION, "Bearer " + token);
        if (body != null) {
            request = request.contentType(APPLICATION_JSON).content(body);
        }
        String response = mockMvc.perform(request)
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return response.isBlank() ? null : json(response);
    }

    private JsonNode patchJson(String token, String url, String body, int expectedStatus) throws Exception {
        String response = mockMvc.perform(patch(url).header(AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return response.isBlank() ? null : json(response);
    }

    private JsonNode getJson(String token, String url, int expectedStatus) throws Exception {
        String response = mockMvc.perform(get(url).header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return response.isBlank() ? null : json(response);
    }

    private JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }
}
