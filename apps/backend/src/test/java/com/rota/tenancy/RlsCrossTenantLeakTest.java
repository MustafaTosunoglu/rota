package com.rota.tenancy;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cross-tenant leak test for the Phase 1A Row-Level Security foundation (plan §15.3).
 *
 * <p>This is the security guarantee of Phase 1A: it connects to a real PostgreSQL as
 * the unprivileged runtime role {@code rota_app} (which has neither superuser nor
 * BYPASSRLS) and proves that, with only {@code app.current_tenant_id} set, a session
 * for Tenant A cannot read, update, or insert rows belonging to Tenant B.
 *
 * <p>Seeding is done over a superuser connection (RLS bypassed) so both tenants' data
 * exists; isolation is then asserted exclusively over {@code rota_app} connections.
 */
@Testcontainers
@DisplayName("RLS cross-tenant isolation (rota_app role)")
class RlsCrossTenantLeakTest {

    /** Ephemeral, throwaway password generated per run — nothing is hardcoded. */
    private static final String ROTA_APP_PASSWORD = "pw_" + UUID.randomUUID().toString().replace("-", "");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private static UUID tenantA;
    private static UUID tenantB;
    private static UUID userA;
    private static UUID userB;

    @BeforeAll
    static void migrateAndSeed() throws SQLException {
        // 1. Apply migrations as the container superuser (owns the tables, bypasses RLS).
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .placeholders(Map.of(
                        "rotaAppPassword", ROTA_APP_PASSWORD,
                        "rotaAdminPassword", ROTA_APP_PASSWORD))
                .load()
                .migrate();

        // 2. Seed two tenants + one user each over a superuser connection (RLS bypassed).
        try (Connection admin = superuserConnection()) {
            tenantA = insertTenant(admin, "tenant-a", "Tenant A");
            tenantB = insertTenant(admin, "tenant-b", "Tenant B");
            userA = insertUser(admin, tenantA, "a@example.com");
            userB = insertUser(admin, tenantB, "b@example.com");
        }
    }

    @Test
    @DisplayName("Tenant A session sees only its own users")
    void tenantAOnlySeesOwnUsers() throws SQLException {
        try (Connection c = appConnection()) {
            setTenant(c, tenantA);

            assertThat(countUsers(c)).isEqualTo(1);
            assertThat(allUserEmails(c)).containsExactly("a@example.com");
            assertThat(userVisibleById(c, userA)).isTrue();
            assertThat(userVisibleById(c, userB)).isFalse();
        }
    }

    @Test
    @DisplayName("Switching tenant context flips visibility to the other tenant")
    void switchingTenantChangesVisibility() throws SQLException {
        try (Connection c = appConnection()) {
            setTenant(c, tenantB);

            assertThat(allUserEmails(c)).containsExactly("b@example.com");
            assertThat(userVisibleById(c, userB)).isTrue();
            assertThat(userVisibleById(c, userA)).isFalse();
        }
    }

    @Test
    @DisplayName("Tenant A also cannot see Tenant B in the tenants table")
    void tenantsTableIsolated() throws SQLException {
        try (Connection c = appConnection()) {
            setTenant(c, tenantA);

            List<UUID> visible = new ArrayList<>();
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT id FROM tenants")) {
                while (rs.next()) {
                    visible.add(rs.getObject("id", UUID.class));
                }
            }
            assertThat(visible).containsExactly(tenantA);
        }
    }

    @Test
    @DisplayName("With NO tenant context set, zero rows are visible (fail closed)")
    void noTenantContextSeesNothing() throws SQLException {
        try (Connection c = appConnection()) {
            // Deliberately do not set app.current_tenant_id.
            assertThat(countUsers(c)).isZero();
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT count(*) FROM tenants")) {
                rs.next();
                assertThat(rs.getInt(1)).isZero();
            }
        }
    }

    @Test
    @DisplayName("Tenant A cannot UPDATE a Tenant B row (0 rows affected)")
    void cannotUpdateOtherTenantRow() throws SQLException {
        try (Connection c = appConnection()) {
            setTenant(c, tenantA);

            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE users SET display_name = 'hijacked' WHERE id = ?")) {
                ps.setObject(1, userB);
                int affected = ps.executeUpdate();
                assertThat(affected).isZero();
            }
        }
    }

    @Test
    @DisplayName("Tenant A cannot INSERT a row for Tenant B (WITH CHECK violation)")
    void cannotInsertRowForOtherTenant() throws SQLException {
        try (Connection c = appConnection()) {
            setTenant(c, tenantA);

            assertThatThrownBy(() -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO users (tenant_id, email) VALUES (?, ?)")) {
                    ps.setObject(1, tenantB);
                    ps.setString(2, "smuggled@example.com");
                    ps.executeUpdate();
                }
            }).isInstanceOf(SQLException.class);
        }
    }

    // ----------------------------------------------------------------------- helpers

    private static Connection superuserConnection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static Connection appConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "rota_app", ROTA_APP_PASSWORD);
    }

    private static void setTenant(Connection c, UUID tenantId) throws SQLException {
        // Plain SET (not SET LOCAL) — fine here because each test uses its own connection.
        try (Statement s = c.createStatement()) {
            s.execute("SET app.current_tenant_id = '" + tenantId + "'");
        }
    }

    private static UUID insertTenant(Connection c, String slug, String name) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO tenants (slug, name) VALUES (?, ?) RETURNING id")) {
            ps.setString(1, slug);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject("id", UUID.class);
            }
        }
    }

    private static UUID insertUser(Connection c, UUID tenantId, String email) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO users (tenant_id, email) VALUES (?, ?) RETURNING id")) {
            ps.setObject(1, tenantId);
            ps.setString(2, email);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject("id", UUID.class);
            }
        }
    }

    private static int countUsers(Connection c) throws SQLException {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT count(*) FROM users")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static List<String> allUserEmails(Connection c) throws SQLException {
        List<String> emails = new ArrayList<>();
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT email FROM users ORDER BY email")) {
            while (rs.next()) {
                emails.add(rs.getString("email"));
            }
        }
        return emails;
    }

    private static boolean userVisibleById(Connection c, UUID id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM users WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
