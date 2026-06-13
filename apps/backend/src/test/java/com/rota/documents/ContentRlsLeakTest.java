package com.rota.documents;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 2 mandatory RLS test (CLAUDE.md): the new content tables must be invisible across
 * tenants — through the API (404, no existence leak) AND at the JDBC layer (count = 0).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("RLS: documents/versions/endpoints of one tenant are invisible to another")
class ContentRlsLeakTest {

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
    @DisplayName("cross-tenant reads return 404 via API and zero rows via JDBC")
    void contentIsTenantIsolated() throws Exception {
        Session alice = createTenantWithContent("alice-rls@example.com", "Alice Rls Org", "Alice API");
        Session bob = createTenantWithContent("bob-rls@example.com", "Bob Rls Org", "Bob API");

        // Alice's listing contains only her document.
        JsonNode aliceDocs = getJson(alice.token, "/api/v1/documents", 200);
        assertThat(aliceDocs).hasSize(1);
        assertThat(aliceDocs.get(0).get("name").asText()).isEqualTo("Alice API");

        // Every direct lookup of Bob's resources behaves as if they do not exist.
        getJson(alice.token, "/api/v1/documents/" + bob.docId, 404);
        getJson(alice.token, "/api/v1/documents/" + bob.docId + "/versions", 404);
        getJson(alice.token, "/api/v1/versions/" + bob.versionId, 404);
        getJson(alice.token, "/api/v1/versions/" + bob.versionId + "/endpoints", 404);
        getJson(alice.token, "/api/v1/versions/" + bob.versionId + "/categories", 404);
        getJson(alice.token, "/api/v1/versions/" + bob.versionId + "/environments", 404);
        getJson(alice.token, "/api/v1/endpoints/" + bob.endpointId, 404);

        // And the same from Bob's side.
        getJson(bob.token, "/api/v1/documents/" + alice.docId, 404);
        getJson(bob.token, "/api/v1/endpoints/" + alice.endpointId, 404);

        // JDBC layer (rota_app + tenant GUC): Bob's rows simply are not there for Alice.
        TenantContext.setTenantId(alice.tenantId);
        for (String table : new String[]{"documents", "document_versions", "categories",
                "endpoints", "endpoint_parameters", "endpoint_request_bodies",
                "endpoint_responses", "environments"}) {
            Integer foreignRows = jdbc.queryForObject(
                    "SELECT count(*) FROM " + table + " WHERE tenant_id = ?", Integer.class, bob.tenantId);
            assertThat(foreignRows).as("%s leak", table).isZero();
        }
        Integer ownDocs = jdbc.queryForObject("SELECT count(*) FROM documents", Integer.class);
        assertThat(ownDocs).isEqualTo(1);
    }

    private record Session(String token, UUID tenantId, UUID docId, UUID versionId, UUID endpointId) {
    }

    /** Registers a tenant and fills it with one document/version/category/endpoint(+sub-rows). */
    private Session createTenantWithContent(String email, String org, String docName) throws Exception {
        JsonNode reg = json(mockMvc.perform(post("/api/v1/auth/register").contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"U","organizationName":"%s"}
                                """.formatted(email, PASSWORD, org)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        UUID tenantId = UUID.fromString(reg.get("tenantId").asText());
        TenantContext.setTenantId(tenantId);
        jdbc.update("UPDATE users SET email_verified = true WHERE email = ?", email);
        TenantContext.clear();
        String token = json(mockMvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("accessToken").asText();

        UUID docId = UUID.fromString(postJson(token, "/api/v1/documents",
                "{\"name\":\"" + docName + "\"}", 201).get("id").asText());
        UUID versionId = UUID.fromString(getJson(token, "/api/v1/documents/" + docId + "/versions", 200)
                .get(0).get("id").asText());
        UUID categoryId = UUID.fromString(postJson(token, "/api/v1/versions/" + versionId + "/categories",
                "{\"name\":\"General\"}", 201).get("id").asText());
        UUID endpointId = UUID.fromString(postJson(token, "/api/v1/versions/" + versionId + "/endpoints", """
                {"method":"GET","path":"/things","categoryId":"%s"}
                """.formatted(categoryId), 201).get("id").asText());
        postJson(token, "/api/v1/endpoints/" + endpointId + "/parameters", """
                {"name":"limit","location":"query"}
                """, 201);
        postJson(token, "/api/v1/endpoints/" + endpointId + "/request-bodies", """
                {"schemaJson":{"type":"object"}}
                """, 201);
        postJson(token, "/api/v1/endpoints/" + endpointId + "/responses", """
                {"statusCode":200}
                """, 201);
        postJson(token, "/api/v1/versions/" + versionId + "/environments", """
                {"name":"test","baseUrl":"https://test.example"}
                """, 201);
        return new Session(token, tenantId, docId, versionId, endpointId);
    }

    private JsonNode postJson(String token, String url, String body, int expectedStatus) throws Exception {
        String response = mockMvc.perform(post(url).header(AUTHORIZATION, "Bearer " + token)
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
