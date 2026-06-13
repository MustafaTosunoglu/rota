package com.rota.endpoints.web;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Endpoints: category/endpoint/parameter/body/response CRUD + clone + draft-only rule")
class EndpointCrudTest {

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
    @DisplayName("full content CRUD, uniqueness, category unlink, clone, immutability after publish")
    void endpointContentCrud() throws Exception {
        String token = registerVerifyLogin("ep-owner@example.com", "Endpoints Org");
        UUID docId = UUID.fromString(postJson(token, "/api/v1/documents", """
                {"name":"Store API"}
                """, 201).get("id").asText());
        UUID v1 = UUID.fromString(getJson(token, "/api/v1/documents/" + docId + "/versions", 200)
                .get(0).get("id").asText());

        // --- categories: ordering by sort_order ---
        UUID usersCat = UUID.fromString(postJson(token, "/api/v1/versions/" + v1 + "/categories", """
                {"name":"Users","sortOrder":1}
                """, 201).get("id").asText());
        postJson(token, "/api/v1/versions/" + v1 + "/categories", """
                {"name":"Orders","sortOrder":0}
                """, 201);
        JsonNode cats = getJson(token, "/api/v1/versions/" + v1 + "/categories", 200);
        assertThat(cats).hasSize(2);
        assertThat(cats.get(0).get("name").asText()).isEqualTo("Orders");

        // --- endpoint create: method upper-cased, path normalised with leading slash ---
        JsonNode created = postJson(token, "/api/v1/versions/" + v1 + "/endpoints", """
                {"method":"get","path":"users/{id}","summary":"Get a user","categoryId":"%s"}
                """.formatted(usersCat), 201);
        UUID endpointId = UUID.fromString(created.get("id").asText());
        assertThat(created.get("method").asText()).isEqualTo("GET");
        assertThat(created.get("path").asText()).isEqualTo("/users/{id}");

        // Duplicate (method, path) within the version → 409.
        postJson(token, "/api/v1/versions/" + v1 + "/endpoints", """
                {"method":"GET","path":"/users/{id}"}
                """, 409);

        // A category from ANOTHER document's version cannot be attached → 404.
        UUID otherDoc = UUID.fromString(postJson(token, "/api/v1/documents", """
                {"name":"Other API"}
                """, 201).get("id").asText());
        UUID otherV1 = UUID.fromString(getJson(token, "/api/v1/documents/" + otherDoc + "/versions", 200)
                .get(0).get("id").asText());
        UUID foreignCat = UUID.fromString(postJson(token, "/api/v1/versions/" + otherV1 + "/categories", """
                {"name":"Foreign"}
                """, 201).get("id").asText());
        postJson(token, "/api/v1/versions/" + v1 + "/endpoints", """
                {"method":"POST","path":"/users","categoryId":"%s"}
                """.formatted(foreignCat), 404);

        // --- endpoint update + clearCategory ---
        JsonNode patched = patchJson(token, "/api/v1/endpoints/" + endpointId, """
                {"summary":"Fetch a user","deprecated":true,"clearCategory":true,"authType":"bearer"}
                """, 200);
        assertThat(patched.get("summary").asText()).isEqualTo("Fetch a user");
        assertThat(patched.get("deprecated").asBoolean()).isTrue();
        assertThat(patched.get("categoryId").isNull()).isTrue();

        // --- parameters ---
        UUID paramId = UUID.fromString(postJson(token, "/api/v1/endpoints/" + endpointId + "/parameters", """
                {"name":"id","location":"path","dataType":"string","required":true,"example":"u_123"}
                """, 201).get("id").asText());
        JsonNode paramPatched = patchJson(token, "/api/v1/parameters/" + paramId, """
                {"description":"User id"}
                """, 200);
        assertThat(paramPatched.get("description").asText()).isEqualTo("User id");

        // --- request bodies: per-content-type unique ---
        UUID bodyId = UUID.fromString(postJson(token, "/api/v1/endpoints/" + endpointId + "/request-bodies", """
                {"schemaJson":{"type":"object"},"exampleJson":{"name":"Ada"}}
                """, 201).get("id").asText());
        postJson(token, "/api/v1/endpoints/" + endpointId + "/request-bodies", """
                {"contentType":"application/json"}
                """, 409);
        patchJson(token, "/api/v1/request-bodies/" + bodyId, """
                {"exampleJson":{"name":"Grace"}}
                """, 200);

        // --- responses ---
        UUID respId = UUID.fromString(postJson(token, "/api/v1/endpoints/" + endpointId + "/responses", """
                {"statusCode":200,"description":"OK","exampleJson":{"id":"u_123"}}
                """, 201).get("id").asText());
        patchJson(token, "/api/v1/responses/" + respId, """
                {"description":"The user"}
                """, 200);

        // --- detail aggregates the three sub-resources ---
        JsonNode detail = getJson(token, "/api/v1/endpoints/" + endpointId, 200);
        assertThat(detail.get("parameters")).hasSize(1);
        assertThat(detail.get("requestBodies")).hasSize(1);
        assertThat(detail.get("responses")).hasSize(1);
        assertThat(detail.get("requestBodies").get(0).get("exampleJson").get("name").asText())
                .isEqualTo("Grace");

        // --- category delete unlinks endpoints (FK SET NULL), does not delete them ---
        UUID tempCat = UUID.fromString(postJson(token, "/api/v1/versions/" + v1 + "/categories", """
                {"name":"Temp"}
                """, 201).get("id").asText());
        patchJson(token, "/api/v1/endpoints/" + endpointId, """
                {"categoryId":"%s"}
                """.formatted(tempCat), 200);
        mockMvc.perform(delete("/api/v1/categories/" + tempCat).header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
        assertThat(getJson(token, "/api/v1/endpoints/" + endpointId, 200).get("categoryId").isNull())
                .isTrue();

        // --- clone copies categories (remapped), endpoints and all sub-resources ---
        patchJson(token, "/api/v1/endpoints/" + endpointId, """
                {"categoryId":"%s"}
                """.formatted(usersCat), 200);
        UUID v2 = UUID.fromString(postJson(token, "/api/v1/documents/" + docId + "/versions", """
                {"versionLabel":"v2","cloneFromVersionId":"%s"}
                """.formatted(v1), 201).get("id").asText());

        JsonNode clonedCats = getJson(token, "/api/v1/versions/" + v2 + "/categories", 200);
        assertThat(clonedCats).hasSize(2);
        JsonNode clonedEndpoints = getJson(token, "/api/v1/versions/" + v2 + "/endpoints", 200);
        assertThat(clonedEndpoints).hasSize(1);
        JsonNode clonedDetail = getJson(token,
                "/api/v1/endpoints/" + clonedEndpoints.get(0).get("id").asText(), 200);
        assertThat(clonedDetail.get("parameters")).hasSize(1);
        assertThat(clonedDetail.get("requestBodies")).hasSize(1);
        assertThat(clonedDetail.get("responses")).hasSize(1);
        // The clone's category is the COPY in v2, not the original v1 category.
        String clonedCategoryId = clonedDetail.get("categoryId").asText();
        assertThat(clonedCategoryId).isNotEqualTo(usersCat.toString());
        assertThat(clonedCats.findValuesAsText("id")).contains(clonedCategoryId);

        // --- published version content is immutable ---
        postJson(token, "/api/v1/versions/" + v1 + "/publish", null, 200);
        postJson(token, "/api/v1/versions/" + v1 + "/endpoints", """
                {"method":"DELETE","path":"/users/{id}"}
                """, 409);
        patchJson(token, "/api/v1/parameters/" + paramId, """
                {"description":"too late"}
                """, 409);
        mockMvc.perform(delete("/api/v1/endpoints/" + endpointId).header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict());
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
