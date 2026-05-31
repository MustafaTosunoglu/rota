package com.rota.iam.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rota.common.tenant.TenantContext;
import com.rota.iam.jpa.RoleEntity;
import com.rota.iam.jpa.RoleRepository;
import com.rota.iam.jpa.UserEntity;
import com.rota.iam.jpa.UserRepository;
import com.rota.iam.jpa.UserRoleEntity;
import com.rota.iam.jpa.UserRoleRepository;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("POST /api/v1/auth/register")
class RegisterEndpointTest {

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
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private UserRoleRepository userRoleRepository;
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("creates tenant + owner user + 4 roles + DEK; password is Argon2id-hashed")
    void registersOwnerAndTenant() throws Exception {
        String body = """
                {"email":"Owner@Example.com","password":"sup3r-secret-pw",
                 "displayName":"Owner One","organizationName":"Acme Inc"}
                """;

        String json = mockMvc.perform(post("/api/v1/auth/register").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode node = objectMapper.readTree(json);
        UUID tenantId = UUID.fromString(node.get("tenantId").asText());
        UUID userId = UUID.fromString(node.get("userId").asText());

        // Read back within the new tenant's context (RLS).
        TenantContext.setTenantId(tenantId);

        UserEntity user = userRepository.findById(userId).orElseThrow();
        assertThat(user.getEmail()).isEqualTo("owner@example.com"); // normalised
        assertThat(user.getPasswordHash()).startsWith("$argon2id$");
        assertThat(user.getPasswordHash()).doesNotContain("sup3r-secret-pw");

        List<RoleEntity> roles = roleRepository.findAll();
        assertThat(roles).extracting(RoleEntity::getName)
                .containsExactlyInAnyOrder("owner", "admin", "editor", "viewer");

        List<UserRoleEntity> assignments = userRoleRepository.findByUserId(userId);
        assertThat(assignments).hasSize(1);
        RoleEntity owner = roleRepository.findByTenantIdAndName(tenantId, "owner").orElseThrow();
        assertThat(assignments.get(0).getRoleId()).isEqualTo(owner.getId());

        byte[] dek = jdbc.queryForObject("SELECT encrypted_dek FROM tenants WHERE id = ?", byte[].class, tenantId);
        assertThat(dek).isNotEmpty();
    }

    @Test
    @DisplayName("duplicate email returns 409")
    void duplicateEmailConflicts() throws Exception {
        String first = """
                {"email":"dup@example.com","password":"sup3r-secret-pw",
                 "displayName":"First","organizationName":"First Org"}
                """;
        mockMvc.perform(post("/api/v1/auth/register").contentType(APPLICATION_JSON).content(first))
                .andExpect(status().isCreated());

        String second = """
                {"email":"dup@example.com","password":"another-secret-pw",
                 "displayName":"Second","organizationName":"Second Org"}
                """;
        mockMvc.perform(post("/api/v1/auth/register").contentType(APPLICATION_JSON).content(second))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("too-short password returns 400")
    void shortPasswordRejected() throws Exception {
        String body = """
                {"email":"weak@example.com","password":"short",
                 "displayName":"Weak","organizationName":"Weak Org"}
                """;
        mockMvc.perform(post("/api/v1/auth/register").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }
}
