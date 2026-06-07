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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Auth rate limiting (per-IP) + access-token blacklist on logout")
class RateLimitAndBlacklistTest {

    private static final String ROTA_APP_PASSWORD = "pw_" + UUID.randomUUID().toString().replace("-", "");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.flyway.placeholders.rotaAppPassword", () -> ROTA_APP_PASSWORD);
        registry.add("spring.flyway.placeholders.rotaAdminPassword", () -> ROTA_APP_PASSWORD);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "rota_app");
        registry.add("spring.datasource.password", () -> ROTA_APP_PASSWORD);
        // Enable the Redis-backed features for THIS test and point them at the container.
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("rota.rate-limit.enabled", () -> "true");
        registry.add("rota.rate-limit.capacity", () -> "10");
        registry.add("rota.token-blacklist.enabled", () -> "true");
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
    @DisplayName("11th auth request from the same IP returns 429")
    void authEndpointsRateLimitedPerIp() throws Exception {
        RequestPostProcessor ip = ip("203.0.113.10");
        // First 10 are allowed through to the controller (wrong creds → 401, but NOT throttled).
        for (int i = 1; i <= 10; i++) {
            mockMvc.perform(post("/api/v1/auth/login").with(ip).contentType(APPLICATION_JSON).content("""
                            {"email":"nobody@example.com","password":"whatever-pw"}
                            """))
                    .andExpect(status().isUnauthorized());
        }
        // The 11th within the window is rejected by the rate limiter.
        mockMvc.perform(post("/api/v1/auth/login").with(ip).contentType(APPLICATION_JSON).content("""
                        {"email":"nobody@example.com","password":"whatever-pw"}
                        """))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    @DisplayName("a different IP has its own bucket (not throttled by another IP's traffic)")
    void rateLimitIsPerIp() throws Exception {
        // A fresh IP can still reach the endpoint even though 203.0.113.10 may be exhausted.
        mockMvc.perform(post("/api/v1/auth/login").with(ip("203.0.113.99")).contentType(APPLICATION_JSON).content("""
                        {"email":"nobody@example.com","password":"whatever-pw"}
                        """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("logout blacklists the access token: a subsequent call with it returns 401")
    void logoutBlacklistsAccessToken() throws Exception {
        RequestPostProcessor ip = ip("203.0.113.20");
        String email = "blk@example.com";
        String password = "sup3r-secret-pw";

        JsonNode reg = json(mockMvc.perform(post("/api/v1/auth/register").with(ip).contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"B","organizationName":"B Org"}
                                """.formatted(email, password)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        UUID tenantId = UUID.fromString(reg.get("tenantId").asText());
        UUID userId = UUID.fromString(reg.get("userId").asText());

        TenantContext.setTenantId(tenantId);
        jdbc.update("UPDATE users SET email_verified = true WHERE id = ?", userId);
        TenantContext.clear();

        JsonNode tokens = json(mockMvc.perform(post("/api/v1/auth/login").with(ip).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String accessToken = tokens.get("accessToken").asText();
        String refreshToken = tokens.get("refreshToken").asText();

        // Valid before logout.
        mockMvc.perform(get("/api/v1/auth/me").header(AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());

        // Logout revokes the refresh token AND blacklists this access token's jti.
        mockMvc.perform(post("/api/v1/auth/logout").header(AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(APPLICATION_JSON).content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isNoContent());

        // The (still-unexpired) access token is now rejected.
        mockMvc.perform(get("/api/v1/auth/me").header(AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    private static RequestPostProcessor ip(String addr) {
        return request -> {
            request.setRemoteAddr(addr);
            return request;
        };
    }

    private JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }
}
