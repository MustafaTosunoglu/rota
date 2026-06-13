package com.rota.consumers.web;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 2E: consumer groups, email-based invitation (invite → accept), access grants
 * (document + endpoint override), role floors (admin to mutate) and cross-tenant RLS.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Consumer groups: invitation flow, access grants, role floors, tenant isolation")
class ConsumerGroupFlowTest {

    private static final String ROTA_APP_PASSWORD = "pw_" + UUID.randomUUID().toString().replace("-", "");
    private static final String PASSWORD = "sup3r-secret-pw";
    private static final Pattern TOKEN = Pattern.compile("accept\\?token=([A-Za-z0-9._~-]+)");

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

    /** Captures the raw invite token from the email that would have gone out. */
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
    private JdbcTemplate jdbc;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private RecordingMailConfig mail;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
        mail.sent.clear();
    }

    @Test
    @DisplayName("invite → accept (cross-tenant consumer), grants, overrides, role floors, RLS")
    void consumerGroupLifecycle() throws Exception {
        // Tenant A: owner + a viewer (to prove the admin floor on mutations).
        UUID tenantA = register("a-owner@example.com", "Org A");
        verify(tenantA, "a-owner@example.com");
        seedUser(tenantA, "a-viewer@example.com", "viewer");
        String ownerA = login("a-owner@example.com");
        String viewerA = login("a-viewer@example.com");

        // A document to grant access to.
        UUID docId = UUID.fromString(postJson(ownerA, "/api/v1/documents", """
                {"name":"Public API"}
                """, 201).get("id").asText());
        UUID versionId = UUID.fromString(getJson(ownerA, "/api/v1/documents/" + docId + "/versions", 200)
                .get(0).get("id").asText());
        UUID endpointId = UUID.fromString(postJson(ownerA, "/api/v1/versions/" + versionId + "/endpoints", """
                {"method":"GET","path":"/ping"}
                """, 201).get("id").asText());

        // --- group creation: admin floor ---
        mockMvc.perform(post("/api/v1/consumer-groups").header(AUTHORIZATION, "Bearer " + viewerA)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"Partners\"}"))
                .andExpect(status().isForbidden());
        UUID groupId = UUID.fromString(postJson(ownerA, "/api/v1/consumer-groups", """
                {"name":"Partners","description":"External partners"}
                """, 201).get("id").asText());
        // Duplicate name → 409.
        postJson(ownerA, "/api/v1/consumer-groups", """
                {"name":"Partners"}
                """, 409);

        // --- invite a member (email-based; invitee has no account yet at invite time) ---
        JsonNode invited = postJson(ownerA, "/api/v1/consumer-groups/" + groupId + "/members", """
                {"email":"Consumer@Partner.com"}
                """, 201);
        assertThat(invited.get("status").asText()).isEqualTo("invited");
        assertThat(invited.get("email").asText()).isEqualTo("consumer@partner.com"); // normalised
        assertThat(invited.get("userId").isNull()).isTrue();
        // Filter to invite emails: the mailbox also holds signup verification emails.
        assertThat(inviteEmails()).hasSize(1);
        String rawToken = extractToken(inviteEmails().get(0));
        assertThat(rawToken).startsWith(tenantA.toString() + ".");

        // Re-inviting a pending member rotates the token and re-sends (still one member row).
        postJson(ownerA, "/api/v1/consumer-groups/" + groupId + "/members", """
                {"email":"consumer@partner.com"}
                """, 201);
        assertThat(inviteEmails()).hasSize(2);
        String rotatedToken = extractToken(inviteEmails().get(1));
        assertThat(rotatedToken).isNotEqualTo(rawToken);
        assertThat(getJson(ownerA, "/api/v1/consumer-groups/" + groupId + "/members", 200)).hasSize(1);

        // The OLD token no longer works after rotation.
        // (acceptance needs an authenticated user — set up the consumer in tenant B first)
        UUID tenantB = register("consumer@partner.com", "Consumer Co");
        verify(tenantB, "consumer@partner.com");
        String consumer = login("consumer@partner.com");

        acceptInvitation(consumer, rawToken, 400);      // rotated away → invalid
        acceptInvitation(consumer, rotatedToken, 204);  // current token works

        // Member is now accepted and linked to the consumer's user id; token cleared.
        JsonNode members = getJson(ownerA, "/api/v1/consumer-groups/" + groupId + "/members", 200);
        assertThat(members).hasSize(1);
        assertThat(members.get(0).get("status").asText()).isEqualTo("accepted");
        assertThat(members.get(0).get("userId").isNull()).isFalse();
        TenantContext.setTenantId(tenantA);
        assertThat(jdbc.queryForObject(
                "SELECT token_hash FROM consumer_group_members WHERE group_id = ?", String.class, groupId))
                .isNull();
        TenantContext.clear();

        // Re-using a consumed token → 400; an already-accepted member can't be re-invited → 409.
        acceptInvitation(consumer, rotatedToken, 400);
        postJson(ownerA, "/api/v1/consumer-groups/" + groupId + "/members", """
                {"email":"consumer@partner.com"}
                """, 409);

        // --- access grants: document + endpoint override (PUT upsert) ---
        JsonNode docGrant = putJson(ownerA,
                "/api/v1/consumer-groups/" + groupId + "/document-access/" + docId, """
                {"canView":true,"canTry":true,"canLoadtest":false}
                """, 200);
        assertThat(docGrant.get("canTry").asBoolean()).isTrue();
        // Upsert: a second PUT updates the same grant in place (still one row).
        putJson(ownerA, "/api/v1/consumer-groups/" + groupId + "/document-access/" + docId, """
                {"canView":true,"canTry":false,"canLoadtest":false}
                """, 200);
        assertThat(getJson(ownerA, "/api/v1/consumer-groups/" + groupId + "/document-access", 200)).hasSize(1);

        putJson(ownerA, "/api/v1/consumer-groups/" + groupId + "/endpoint-access/" + endpointId, """
                {"canView":true,"canTry":true,"canLoadtest":false}
                """, 200);
        assertThat(getJson(ownerA, "/api/v1/consumer-groups/" + groupId + "/endpoint-access", 200)).hasSize(1);

        // Granting access to a non-existent / other-tenant document → 404 (guard via documents.api).
        putJson(ownerA, "/api/v1/consumer-groups/" + groupId + "/document-access/" + UUID.randomUUID(), """
                {"canView":true,"canTry":false,"canLoadtest":false}
                """, 404);

        // --- role floor on access mutation: viewer cannot, editors below admin cannot ---
        mockMvc.perform(put("/api/v1/consumer-groups/" + groupId + "/document-access/" + docId)
                        .header(AUTHORIZATION, "Bearer " + viewerA)
                        .contentType(APPLICATION_JSON)
                        .content("{\"canView\":true,\"canTry\":true,\"canLoadtest\":true}"))
                .andExpect(status().isForbidden());

        // --- cross-tenant RLS: tenant B sees none of A's groups (and cannot touch them) ---
        assertThat(getJson(consumer, "/api/v1/consumer-groups", 200)).isEmpty();
        // B is a viewer-less brand-new owner, so it has admin; still, A's group is invisible → 404.
        getJson(consumer, "/api/v1/consumer-groups/" + groupId, 404);
        putJson(consumer, "/api/v1/consumer-groups/" + groupId + "/document-access/" + docId, """
                {"canView":true,"canTry":false,"canLoadtest":false}
                """, 404);

        // JDBC layer: B's tenant context sees zero of A's consumer rows.
        TenantContext.setTenantId(tenantB);
        for (String table : new String[]{"consumer_groups", "consumer_group_members",
                "consumer_group_document_access", "consumer_group_endpoint_access"}) {
            Integer leak = jdbc.queryForObject(
                    "SELECT count(*) FROM " + table + " WHERE tenant_id = ?", Integer.class, tenantA);
            assertThat(leak).as("%s leak", table).isZero();
        }
        TenantContext.clear();

        // --- deleting the group cascades members + grants ---
        mockMvc.perform(delete("/api/v1/consumer-groups/" + groupId).header(AUTHORIZATION, "Bearer " + ownerA))
                .andExpect(status().isNoContent());
        getJson(ownerA, "/api/v1/consumer-groups/" + groupId, 404);
        TenantContext.setTenantId(tenantA);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM consumer_group_members", Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM consumer_group_document_access", Integer.class)).isZero();
        TenantContext.clear();
    }

    // --- helpers ---------------------------------------------------------------------

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

    private void acceptInvitation(String token, String rawToken, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/v1/invitations/accept").header(AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON).content("{\"token\":\"" + rawToken + "\"}"))
                .andExpect(status().is(expectedStatus));
    }

    /** Invite emails only (the mailbox also captures signup verification emails). */
    private List<String> inviteEmails() {
        return mail.sent.stream()
                .filter(m -> m[1].contains("invited to API docs"))
                .map(m -> m[2])
                .toList();
    }

    private String extractToken(String emailBody) {
        Matcher matcher = TOKEN.matcher(emailBody);
        assertThat(matcher.find()).as("invite email contains a token link").isTrue();
        return matcher.group(1);
    }

    private JsonNode postJson(String token, String url, String body, int expectedStatus) throws Exception {
        String response = mockMvc.perform(post(url).header(AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return response.isBlank() ? null : json(response);
    }

    private JsonNode putJson(String token, String url, String body, int expectedStatus) throws Exception {
        String response = mockMvc.perform(put(url).header(AUTHORIZATION, "Bearer " + token)
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
