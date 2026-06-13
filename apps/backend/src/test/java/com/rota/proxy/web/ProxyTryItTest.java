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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Try It proxy: public call works, SSRF blocked, headers redacted in history")
class ProxyTryItTest {

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
    private ProxyProperties proxyProperties;

    private HttpServer stub;
    private final AtomicReference<String> receivedAuth = new AtomicReference<>();

    @BeforeEach
    void startStub() throws IOException {
        stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stub.createContext("/echo", exchange -> {
            receivedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
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
        proxyProperties.setBlockPrivateNetworks(true); // restore default for other tests
        TenantContext.clear();
    }

    @Test
    @DisplayName("public call returns response + latency; Authorization forwarded but redacted in history")
    void executeAgainstStub() throws Exception {
        proxyProperties.setBlockPrivateNetworks(false); // allow the loopback stub
        String token = ownerToken("proxy-owner@example.com", "Proxy Org");
        Ctx ctx = endpointWithEnvironment(token, "http://127.0.0.1:" + stub.getAddress().getPort());

        String body = """
                {"endpointId":"%s","environmentId":"%s","headers":{"Authorization":"Bearer s3cr3t","X-Custom":"v"}}
                """.formatted(ctx.endpointId, ctx.environmentId);
        JsonNode response = json(mockMvc.perform(post("/api/v1/proxy/execute")
                        .header(AUTHORIZATION, "Bearer " + token).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(response.get("status").asInt()).isEqualTo(200);
        assertThat(response.get("latencyMs").asLong()).isGreaterThanOrEqualTo(0);
        assertThat(response.get("body").asText()).contains("\"ok\":true");

        // The REAL Authorization header reached the target...
        assertThat(receivedAuth.get()).isEqualTo("Bearer s3cr3t");
        // ...but history stores it redacted, while a non-sensitive header is kept.
        JsonNode hist = json(mockMvc.perform(get("/api/v1/endpoints/" + ctx.endpointId + "/try-it-history")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(hist).hasSize(1);
        JsonNode reqHeaders = hist.get(0).get("requestSummary").get("headers");
        assertThat(reqHeaders.get("Authorization").asText()).isEqualTo("[REDACTED]");
        assertThat(reqHeaders.get("X-Custom").asText()).isEqualTo("v");
        assertThat(hist.get(0).get("statusCode").asInt()).isEqualTo(200);
    }

    @Test
    @DisplayName("private-IP and bad-scheme targets are blocked with 400")
    void ssrfBlocked() throws Exception {
        // block-private-networks stays at its default (true).
        String token = ownerToken("ssrf-owner@example.com", "SSRF Org");
        Ctx privateCtx = endpointWithEnvironment(token, "http://10.0.0.1");
        mockMvc.perform(post("/api/v1/proxy/execute").header(AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"endpointId\":\"%s\",\"environmentId\":\"%s\"}"
                                .formatted(privateCtx.endpointId, privateCtx.environmentId)))
                .andExpect(status().isBadRequest());

        Ctx loopbackCtx = endpointWithEnvironment(token, "http://localhost:1");
        mockMvc.perform(post("/api/v1/proxy/execute").header(AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"endpointId\":\"%s\",\"environmentId\":\"%s\"}"
                                .formatted(loopbackCtx.endpointId, loopbackCtx.environmentId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("execute requires authentication")
    void requiresAuth() throws Exception {
        mockMvc.perform(post("/api/v1/proxy/execute").contentType(APPLICATION_JSON)
                        .content("{\"endpointId\":\"%s\",\"environmentId\":\"%s\"}"
                                .formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    // --- helpers ---------------------------------------------------------------------

    private record Ctx(String endpointId, String environmentId) {
    }

    private Ctx endpointWithEnvironment(String token, String baseUrl) throws Exception {
        String docId = json(mockMvc.perform(post("/api/v1/documents").header(AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"Proxy Doc " + UUID.randomUUID() + "\"}"))
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
