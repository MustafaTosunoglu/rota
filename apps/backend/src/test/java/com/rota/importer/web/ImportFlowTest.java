package com.rota.importer.web;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Import/Export: OpenAPI + Postman parse→apply→export, dedup, roles, tenant isolation")
class ImportFlowTest {

    private static final String ROTA_APP_PASSWORD = "pw_" + UUID.randomUUID().toString().replace("-", "");
    private static final String PASSWORD = "sup3r-secret-pw";

    private static final String PETSTORE = """
            {
              "openapi": "3.0.3",
              "info": {"title": "Swagger Petstore", "version": "1.0.0"},
              "servers": [{"url": "https://petstore.example.com/v1", "description": "prod"}],
              "tags": [{"name": "pets", "description": "Pet operations"}],
              "paths": {
                "/pets": {
                  "get": {"tags": ["pets"], "summary": "List pets",
                    "parameters": [{"name": "limit", "in": "query", "schema": {"type": "integer"}}],
                    "responses": {"200": {"description": "ok"}}},
                  "post": {"tags": ["pets"], "summary": "Create pet",
                    "requestBody": {"content": {"application/json": {"schema": {"type": "object",
                      "properties": {"name": {"type": "string"}}}}}},
                    "responses": {"201": {"description": "created"}}}
                },
                "/pets/{petId}": {
                  "get": {"tags": ["pets"], "summary": "Get pet",
                    "parameters": [{"name": "petId", "in": "path", "required": true, "schema": {"type": "string"}}],
                    "responses": {"200": {"description": "ok"}}},
                  "delete": {"tags": ["pets"], "summary": "Delete pet", "security": [{"bearerAuth": []}],
                    "parameters": [{"name": "petId", "in": "path", "required": true, "schema": {"type": "string"}}],
                    "responses": {"204": {"description": "gone"}}}
                }
              },
              "components": {"securitySchemes": {"bearerAuth": {"type": "http", "scheme": "bearer"}}}
            }
            """;

    private static final String POSTMAN = """
            {
              "info": {"name": "Orders API", "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"},
              "item": [
                {"name": "Orders", "item": [
                  {"name": "List orders", "request": {"method": "GET",
                    "url": {"raw": "https://api.example.com/orders?status=open",
                      "host": ["api", "example", "com"], "path": ["orders"],
                      "query": [{"key": "status", "value": "open"}]}}},
                  {"name": "Create order", "request": {"method": "POST",
                    "header": [{"key": "Content-Type", "value": "application/json"}],
                    "body": {"mode": "raw", "raw": "{\\"item\\": \\"book\\"}",
                      "options": {"raw": {"language": "json"}}},
                    "url": {"raw": "https://api.example.com/orders", "path": ["orders"]}}}
                ]}
              ]
            }
            """;

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
    @DisplayName("OpenAPI: parse → apply → endpoints/categories/env persisted → dedup → export round-trips")
    void openApiImportExportDedup() throws Exception {
        String token = ownerWithVerifiedEmail("oa-owner@example.com", "OA Org");
        UUID versionId = newDraftVersion(token, "Pet Docs");

        // Parse (preview, no writes).
        JsonNode parsed = parse(token, "openapi", PETSTORE);
        assertThat(parsed.get("suggestedTitle").asText()).isEqualTo("Swagger Petstore");
        assertThat(parsed.get("endpoints")).hasSize(4);
        assertThat(parsed.get("categories").get(0).get("name").asText()).isEqualTo("pets");
        assertThat(parsed.get("environments")).hasSize(1);

        // Apply.
        JsonNode result = apply(token, versionId, parsed, "OVERWRITE");
        assertThat(result.get("created").asInt()).isEqualTo(4);

        // Persisted: 4 endpoints, one category "pets", one environment.
        assertThat(getJson(token, "/api/v1/versions/" + versionId + "/endpoints")).hasSize(4);
        JsonNode categories = getJson(token, "/api/v1/versions/" + versionId + "/categories");
        assertThat(categories).hasSize(1);
        assertThat(categories.get(0).get("name").asText()).isEqualTo("pets");
        assertThat(getJson(token, "/api/v1/versions/" + versionId + "/environments")).hasSize(1);

        // The DELETE /pets/{petId} endpoint kept its bearer auth + path param.
        JsonNode endpoints = getJson(token, "/api/v1/versions/" + versionId + "/endpoints");
        String deleteId = null;
        for (JsonNode ep : endpoints) {
            if (ep.get("method").asText().equals("DELETE")) {
                deleteId = ep.get("id").asText();
            }
        }
        assertThat(deleteId).isNotNull();
        JsonNode detail = getJson(token, "/api/v1/endpoints/" + deleteId);
        assertThat(detail.get("authType").asText()).isEqualTo("bearer");
        assertThat(detail.get("parameters").get(0).get("name").asText()).isEqualTo("petId");
        assertThat(detail.get("parameters").get(0).get("location").asText()).isEqualTo("path");

        // Dedup SKIP: nothing changes.
        JsonNode skipResult = apply(token, versionId, parsed, "SKIP");
        assertThat(skipResult.get("skipped").asInt()).isEqualTo(4);
        assertThat(skipResult.get("created").asInt()).isZero();
        assertThat(getJson(token, "/api/v1/versions/" + versionId + "/endpoints")).hasSize(4);

        // Dedup OVERWRITE: replaces in place, count stays 4.
        JsonNode overwriteResult = apply(token, versionId, parsed, "OVERWRITE");
        assertThat(overwriteResult.get("overwritten").asInt()).isEqualTo(4);
        assertThat(getJson(token, "/api/v1/versions/" + versionId + "/endpoints")).hasSize(4);

        // Export round-trips the operations.
        JsonNode spec = getJson(token, "/api/v1/versions/" + versionId + "/export/openapi");
        assertThat(spec.get("openapi").asText()).startsWith("3.");
        assertThat(spec.get("paths").has("/pets")).isTrue();
        assertThat(spec.get("paths").has("/pets/{petId}")).isTrue();
        assertThat(spec.get("paths").get("/pets").has("get")).isTrue();
        assertThat(spec.get("paths").get("/pets").has("post")).isTrue();
        assertThat(spec.get("paths").get("/pets/{petId}").get("delete").get("deprecated")).isNull();
        assertThat(spec.get("servers").get(0).get("url").asText()).isEqualTo("https://petstore.example.com/v1");
    }

    @Test
    @DisplayName("Postman: folders→categories, requests→endpoints, raw JSON body→example")
    void postmanImport() throws Exception {
        String token = ownerWithVerifiedEmail("pm-owner@example.com", "PM Org");
        UUID versionId = newDraftVersion(token, "Orders Docs");

        JsonNode parsed = parse(token, "postman", POSTMAN);
        assertThat(parsed.get("suggestedTitle").asText()).isEqualTo("Orders API");
        assertThat(parsed.get("endpoints")).hasSize(2);

        apply(token, versionId, parsed, "OVERWRITE");
        JsonNode endpoints = getJson(token, "/api/v1/versions/" + versionId + "/endpoints");
        assertThat(endpoints).hasSize(2);
        assertThat(endpoints).extracting(e -> e.get("method").asText()).contains("GET", "POST");
        assertThat(getJson(token, "/api/v1/versions/" + versionId + "/categories"))
                .anySatisfy(c -> assertThat(c.get("name").asText()).isEqualTo("Orders"));
    }

    @Test
    @DisplayName("role floor: viewer cannot parse/import; cross-tenant version is 404")
    void rolesAndTenantIsolation() throws Exception {
        UUID tenantA = register("imp-owner@example.com", "Imp Org");
        verify(tenantA, "imp-owner@example.com");
        seedUser(tenantA, "imp-viewer@example.com", "viewer");
        String owner = login("imp-owner@example.com");
        String viewer = login("imp-viewer@example.com");

        // Viewer is blocked from the import endpoints (editor floor).
        mockMvc.perform(post("/api/v1/import/parse").header(AUTHORIZATION, "Bearer " + viewer)
                        .contentType(APPLICATION_JSON)
                        .content("{\"format\":\"openapi\",\"content\":" + quote(PETSTORE) + "}"))
                .andExpect(status().isForbidden());

        // Owner imports into their own version fine.
        UUID versionA = newDraftVersion(owner, "A Docs");
        JsonNode parsed = parse(owner, "openapi", PETSTORE);
        apply(owner, versionA, parsed, "OVERWRITE");

        // A different tenant cannot import into tenant A's version (RLS → 404).
        UUID tenantB = register("imp-owner-b@example.com", "Imp Org B");
        verify(tenantB, "imp-owner-b@example.com");
        String ownerB = login("imp-owner-b@example.com");
        mockMvc.perform(post("/api/v1/versions/" + versionA + "/import")
                        .header(AUTHORIZATION, "Bearer " + ownerB)
                        .contentType(APPLICATION_JSON)
                        .content("{\"parsed\":" + parsed + ",\"dedupMode\":\"OVERWRITE\"}"))
                .andExpect(status().isNotFound());
        // And cannot export it.
        mockMvc.perform(get("/api/v1/versions/" + versionA + "/export/openapi")
                        .header(AUTHORIZATION, "Bearer " + ownerB))
                .andExpect(status().isNotFound());
    }

    // --- helpers ---------------------------------------------------------------------

    private String ownerWithVerifiedEmail(String email, String org) throws Exception {
        UUID tenantId = register(email, org);
        verify(tenantId, email);
        return login(email);
    }

    private UUID register(String email, String org) throws Exception {
        JsonNode reg = json(mockMvc.perform(post("/api/v1/auth/register").contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"U","organizationName":"%s"}
                                """.formatted(email, PASSWORD, org)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        return UUID.fromString(reg.get("tenantId").asText());
    }

    private void verify(UUID tenantId, String email) {
        TenantContext.setTenantId(tenantId);
        jdbc.update("UPDATE users SET email_verified = true WHERE email = ?", email);
        TenantContext.clear();
    }

    private void seedUser(UUID tenantId, String email, String roleName) {
        TenantContext.setTenantId(tenantId);
        UUID userId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users (id, tenant_id, email, email_verified, password_hash, display_name)
                VALUES (?, ?, ?, true, ?, ?)
                """, userId, tenantId, email, passwordEncoder.encode(PASSWORD), roleName);
        jdbc.update("""
                INSERT INTO user_roles (user_id, role_id, tenant_id)
                SELECT ?, id, ? FROM roles WHERE name = ?
                """, userId, tenantId, roleName);
        TenantContext.clear();
    }

    private String login(String email) throws Exception {
        return json(mockMvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private UUID newDraftVersion(String token, String docName) throws Exception {
        JsonNode doc = json(mockMvc.perform(post("/api/v1/documents").header(AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"" + docName + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        JsonNode versions = getJson(token, "/api/v1/documents/" + doc.get("id").asText() + "/versions");
        return UUID.fromString(versions.get(0).get("id").asText());
    }

    private JsonNode parse(String token, String format, String content) throws Exception {
        String response = mockMvc.perform(post("/api/v1/import/parse").header(AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"format\":\"" + format + "\",\"content\":" + quote(content) + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json(response);
    }

    private JsonNode apply(String token, UUID versionId, JsonNode parsed, String dedup) throws Exception {
        String response = mockMvc.perform(post("/api/v1/versions/" + versionId + "/import")
                        .header(AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"parsed\":" + parsed + ",\"dedupMode\":\"" + dedup + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json(response);
    }

    private JsonNode getJson(String token, String url) throws Exception {
        return json(mockMvc.perform(get(url).header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String quote(String raw) throws Exception {
        return objectMapper.writeValueAsString(raw);
    }

    private JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }
}
