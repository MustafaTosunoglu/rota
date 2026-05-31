package com.rota.common.encryption;

import com.rota.common.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.SecretKey;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Faz 1 acceptance for encryption: a per-tenant DEK is generated, stored (wrapped) in
 * {@code tenants.encrypted_dek} as bytea under RLS, and round-trips — decrypting back to a
 * usable key that recovers the original plaintext.
 */
@SpringBootTest
@Testcontainers
@DisplayName("Per-tenant DEK provisioning + round-trip under RLS")
class TenantDekServiceRlsTest {

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
    private TenantDekService tenantDekService;
    @Autowired
    private EncryptionService encryptionService;
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void provisionsAndRoundTripsTenantDek() {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        jdbc.update("INSERT INTO tenants (id, slug, name) VALUES (?, ?, ?)", tenantId, "dek-tenant", "DEK Tenant");

        // No DEK yet.
        assertThat(jdbc.queryForObject("SELECT encrypted_dek FROM tenants WHERE id = ?", byte[].class, tenantId))
                .isNull();

        tenantDekService.provisionDek(tenantId);

        // Stored as non-null bytea.
        byte[] stored = jdbc.queryForObject("SELECT encrypted_dek FROM tenants WHERE id = ?", byte[].class, tenantId);
        assertThat(stored).isNotEmpty();

        // Resolve from DB (drop cache first) and prove a field round-trips.
        tenantDekService.evict(tenantId);
        SecretKey dek = tenantDekService.resolveCurrentTenantDek();
        byte[] ciphertext = encryptionService.encryptWithDek("X-API-Key: secret-value", dek);
        assertThat(encryptionService.decryptWithDek(ciphertext, dek)).isEqualTo("X-API-Key: secret-value");
    }
}
