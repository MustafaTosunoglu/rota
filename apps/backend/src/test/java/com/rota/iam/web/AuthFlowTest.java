package com.rota.iam.web;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Auth flow: register → login → /me → refresh (rotation) → logout")
class AuthFlowTest {

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
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("full happy path with refresh rotation and logout revocation")
    void fullAuthFlow() throws Exception {
        // 1. Register.
        String registerBody = """
                {"email":"flow@example.com","password":"sup3r-secret-pw",
                 "displayName":"Flow User","organizationName":"Flow Org"}
                """;
        JsonNode reg = json(mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(APPLICATION_JSON).content(registerBody))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        UUID tenantId = UUID.fromString(reg.get("tenantId").asText());
        UUID userId = UUID.fromString(reg.get("userId").asText());

        // 2. Simulate email verification (real flow arrives in 1D-4).
        TenantContext.setTenantId(tenantId);
        jdbc.update("UPDATE users SET email_verified = true WHERE id = ?", userId);
        TenantContext.clear();

        // 3. Login.
        JsonNode tokens = json(mockMvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON).content("""
                        {"email":"flow@example.com","password":"sup3r-secret-pw"}
                        """))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String accessToken = tokens.get("accessToken").asText();
        String refreshToken = tokens.get("refreshToken").asText();
        assertThat(refreshToken).startsWith(tenantId.toString() + ".");

        // 4. /me with the access token returns the identity + owner role.
        mockMvc.perform(get("/api/v1/auth/me").header(AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("flow@example.com"))
                .andExpect(jsonPath("$.roles[0]").value("owner"));

        // 5. /me without a token is rejected.
        mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());

        // 6. Refresh rotates the token.
        JsonNode refreshed = json(mockMvc.perform(post("/api/v1/auth/refresh").contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String newAccess = refreshed.get("accessToken").asText();
        String newRefresh = refreshed.get("refreshToken").asText();
        assertThat(newRefresh).isNotEqualTo(refreshToken);

        // 7. Reusing the OLD (now rotated/revoked) refresh token is rejected.
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());

        // 8. Logout (authenticated) revokes the current refresh token.
        mockMvc.perform(post("/api/v1/auth/logout").header(AUTHORIZATION, "Bearer " + newAccess)
                        .contentType(APPLICATION_JSON).content("{\"refreshToken\":\"" + newRefresh + "\"}"))
                .andExpect(status().isNoContent());

        // 9. The logged-out refresh token no longer works.
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + newRefresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("login with wrong password returns 401")
    void wrongPasswordRejected() throws Exception {
        String registerBody = """
                {"email":"creds@example.com","password":"correct-horse-battery",
                 "displayName":"Creds","organizationName":"Creds Org"}
                """;
        JsonNode reg = json(mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(APPLICATION_JSON).content(registerBody))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        UUID tenantId = UUID.fromString(reg.get("tenantId").asText());
        UUID userId = UUID.fromString(reg.get("userId").asText());
        TenantContext.setTenantId(tenantId);
        jdbc.update("UPDATE users SET email_verified = true WHERE id = ?", userId);
        TenantContext.clear();

        mockMvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON).content("""
                        {"email":"creds@example.com","password":"WRONG-password"}
                        """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("login before email verification returns 403")
    void unverifiedEmailRejected() throws Exception {
        String registerBody = """
                {"email":"unverified@example.com","password":"correct-horse-battery",
                 "displayName":"Unverified","organizationName":"Unverified Org"}
                """;
        mockMvc.perform(post("/api/v1/auth/register").contentType(APPLICATION_JSON).content(registerBody))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON).content("""
                        {"email":"unverified@example.com","password":"correct-horse-battery"}
                        """))
                .andExpect(status().isForbidden());
    }

    private JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }
}
