package com.rota.common.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("ResendEmailSender: correct API call, no exception leaks on failure")
class ResendEmailSenderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EmailProperties properties = new EmailProperties();

    private HttpServer server;
    private ResendEmailSender sender;
    private final AtomicReference<String> capturedAuth = new AtomicReference<>();
    private final AtomicReference<String> capturedBody = new AtomicReference<>();

    @BeforeEach
    void startFakeResend() throws Exception {
        properties.setFrom("Rota <no-reply@rota.dev>");
        properties.setResendApiKey("re_test_key");

        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/emails", exchange -> {
            capturedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] ok = "{\"id\":\"fake\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, ok.length);
            exchange.getResponseBody().write(ok);
            exchange.close();
        });
        server.start();
        sender = new ResendEmailSender(HttpClient.newHttpClient(), objectMapper, properties,
                "http://localhost:" + server.getAddress().getPort());
    }

    @AfterEach
    void stopFakeResend() {
        server.stop(0);
    }

    @Test
    @DisplayName("POSTs bearer-authenticated JSON with from/to/subject/html")
    void sendsExpectedPayload() throws Exception {
        sender.send("user@example.com", "Verify your email", "<p>hello</p>");

        assertThat(capturedAuth.get()).isEqualTo("Bearer re_test_key");
        JsonNode body = objectMapper.readTree(capturedBody.get());
        assertThat(body.get("from").asText()).isEqualTo("Rota <no-reply@rota.dev>");
        assertThat(body.get("to").get(0).asText()).isEqualTo("user@example.com");
        assertThat(body.get("subject").asText()).isEqualTo("Verify your email");
        assertThat(body.get("html").asText()).isEqualTo("<p>hello</p>");
    }

    @Test
    @DisplayName("API/network failures are swallowed (logged), never thrown to the caller")
    void failuresDoNotPropagate() {
        // Unreachable endpoint: same EmailSender contract as SMTP — the triggering user
        // operation (signup, password reset) must not break when email delivery fails.
        ResendEmailSender broken = new ResendEmailSender(HttpClient.newHttpClient(), objectMapper,
                properties, "http://localhost:1");
        assertThatCode(() -> broken.send("user@example.com", "s", "<p>b</p>"))
                .doesNotThrowAnyException();
    }
}
