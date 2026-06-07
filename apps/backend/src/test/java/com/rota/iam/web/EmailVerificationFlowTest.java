package com.rota.iam.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rota.common.email.EmailSender;
import com.rota.common.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Email verify + password reset flow")
class EmailVerificationFlowTest {

    private static final String ROTA_APP_PASSWORD = "pw_" + java.util.UUID.randomUUID().toString().replace("-", "");
    private static final Pattern TOKEN = Pattern.compile("token=([^\"]+)");

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

    /** Captures the raw token by intercepting the email that would have gone to maildev. */
    @TestConfiguration
    static class RecordingMailConfig {
        final List<String[]> sent = new CopyOnWriteArrayList<>(); // [to, subject, body]

        @Bean
        @Primary
        EmailSender recordingEmailSender() {
            return (to, subject, body) -> sent.add(new String[]{to, subject, body});
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RecordingMailConfig mail;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
        mail.sent.clear();
    }

    @Test
    @DisplayName("register → verify → login; then forgot → reset; old sessions revoked")
    void fullVerifyAndResetFlow() throws Exception {
        String email = "verify@example.com";
        String password = "sup3r-secret-pw";

        // 1. Register → a verification email is sent automatically.
        mockMvc.perform(post("/api/v1/auth/register").contentType(APPLICATION_JSON).content("""
                        {"email":"%s","password":"%s","displayName":"V","organizationName":"V Org"}
                        """.formatted(email, password)))
                .andExpect(status().isCreated());
        String verifyToken = lastTokenFor("Verify");
        assertThat(verifyToken).isNotBlank();

        // 2. Login before verification → 403.
        login(email, password).andExpect(status().isForbidden());

        // 3. Verify the email.
        verifyEmail(verifyToken).andExpect(status().isNoContent());

        // 4. Reusing the same verification token → 400 (single-use).
        verifyEmail(verifyToken).andExpect(status().isBadRequest());

        // 5. Login now succeeds; keep the refresh token for the revocation check.
        JsonNode tokens = json(login(email, password).andExpect(status().isOk()));
        String oldRefresh = tokens.get("refreshToken").asText();

        // 6. Forgot password → a reset email is sent.
        mockMvc.perform(post("/api/v1/auth/forgot-password").contentType(APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isNoContent());
        String resetToken = lastTokenFor("Reset");
        assertThat(resetToken).isNotBlank();

        // 7. Reset the password.
        String newPassword = "brand-new-pw-456";
        resetPassword(resetToken, newPassword).andExpect(status().isNoContent());

        // 8. Old password no longer works; new one does.
        login(email, password).andExpect(status().isUnauthorized());
        login(email, newPassword).andExpect(status().isOk());

        // 9. Reset revoked all prior sessions: the pre-reset refresh token is dead.
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + oldRefresh + "\"}"))
                .andExpect(status().isUnauthorized());

        // 10. Reusing the reset token → 400 (single-use).
        resetPassword(resetToken, "yet-another-pw-789").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("forgot-password for an unknown email returns 204 (no account enumeration)")
    void forgotPasswordUnknownEmailIsSilent() throws Exception {
        mockMvc.perform(post("/api/v1/auth/forgot-password").contentType(APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\"}"))
                .andExpect(status().isNoContent());
        assertThat(mail.sent).isEmpty();
    }

    @Test
    @DisplayName("verify-email with a malformed token returns 400")
    void verifyEmailMalformedToken() throws Exception {
        verifyEmail("not-a-valid-token").andExpect(status().isBadRequest());
    }

    // --- helpers -------------------------------------------------------------------------

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON).content("""
                {"email":"%s","password":"%s"}
                """.formatted(email, password)));
    }

    private org.springframework.test.web.servlet.ResultActions verifyEmail(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/verify-email").contentType(APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions resetPassword(String token, String newPassword)
            throws Exception {
        return mockMvc.perform(post("/api/v1/auth/reset-password").contentType(APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + newPassword + "\"}"));
    }

    /** Pull the token out of the most recent email whose subject contains {@code subjectPart}. */
    private String lastTokenFor(String subjectPart) {
        for (int i = mail.sent.size() - 1; i >= 0; i--) {
            String[] msg = mail.sent.get(i);
            if (msg[1].contains(subjectPart)) {
                Matcher m = TOKEN.matcher(msg[2]);
                if (m.find()) {
                    return m.group(1);
                }
            }
        }
        return null;
    }

    private JsonNode json(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }
}
