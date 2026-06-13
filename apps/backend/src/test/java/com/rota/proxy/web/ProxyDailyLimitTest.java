package com.rota.proxy.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rota.common.tenant.TenantContext;
import com.rota.proxy.internal.ProxyProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
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
@DisplayName("Try It daily quota (Free tier): the over-limit request returns 429")
class ProxyDailyLimitTest {

    private static final String ROTA_APP_PASSWORD = "pw_" + UUID.randomUUID().toString().replace("-", "");
    private static final String PASSWORD = "sup3r-secret-pw";

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
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        // Enable the quota with a tiny limit so the test is fast; allow the loopback stub.
        registry.add("rota.proxy.daily-limit-enabled", () -> "true");
        registry.add("rota.proxy.daily-free-limit", () -> "2");
        registry.add("rota.proxy.block-private-networks", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ProxyProperties proxyProperties;

    private HttpServer stub;

    @BeforeEach
    void startStub() throws IOException {
        stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stub.createContext("/echo", exchange -> {
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        stub.start();
    }

    @AfterEach
    void cleanup() {
        stub.stop(0);
        TenantContext.clear();
    }

    @Test
    @DisplayName("limit=2: 1st & 2nd succeed, 3rd is 429; quota endpoint reports remaining")
    void dailyLimitEnforced() throws Exception {
        assertThat(proxyProperties.isDailyLimitEnabled()).isTrue();
        String token = ownerToken("quota-owner@example.com", "Quota Org");
        Ctx ctx = endpointWithEnvironment(token, "http://127.0.0.1:" + stub.getAddress().getPort());

        String body = "{\"endpointId\":\"%s\",\"environmentId\":\"%s\"}".formatted(ctx.endpointId, ctx.environmentId);

        execute(token, body, 200);
        execute(token, body, 200);
        execute(token, body, 429); // over the daily limit of 2

        JsonNode quota = json(mockMvc.perform(get("/api/v1/proxy/quota").header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(quota.get("limit").asInt()).isEqualTo(2);
        assertThat(quota.get("remaining").asLong()).isZero();
    }

    private void execute(String token, String body, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/v1/proxy/execute").header(AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus));
    }

    private record Ctx(String endpointId, String environmentId) {
    }

    private Ctx endpointWithEnvironment(String token, String baseUrl) throws Exception {
        String docId = json(mockMvc.perform(post("/api/v1/documents").header(AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"Quota Doc\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        String versionId = json(mockMvc.perform(get("/api/v1/documents/" + docId + "/versions")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get(0).get("id").asText();
        String endpointId = json(mockMvc.perform(post("/api/v1/versions/" + versionId + "/endpoints")
                        .header(AUTHORIZATION, "Bearer " + token).contentType(APPLICATION_JSON)
                        .content("{\"method\":\"GET\",\"path\":\"/echo\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        String environmentId = json(mockMvc.perform(post("/api/v1/versions/" + versionId + "/environments")
                        .header(AUTHORIZATION, "Bearer " + token).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"test\",\"baseUrl\":\"" + baseUrl + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        return new Ctx(endpointId, environmentId);
    }

    private String ownerToken(String email, String org) throws Exception {
        JsonNode reg = json(mockMvc.perform(post("/api/v1/auth/register").contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"U","organizationName":"%s"}
                                """.formatted(email, PASSWORD, org)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        UUID tenantId = UUID.fromString(reg.get("tenantId").asText());
        TenantContext.setTenantId(tenantId);
        jdbc.update("UPDATE users SET email_verified = true WHERE email = ?", email);
        TenantContext.clear();
        return json(mockMvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("accessToken").asText();
    }

    private JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }
}
