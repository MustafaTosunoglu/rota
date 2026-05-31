package com.rota.common.tenant;

import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Wraps the real {@link DataSource} so that every borrowed connection has the Postgres
 * session GUC {@code app.current_tenant_id} aligned with {@link TenantContext} before it
 * is handed to Hibernate / JdbcTemplate. This is what makes Row-Level Security effective
 * at runtime (plan §8.1, task 1.6).
 *
 * <p>Chosen over an AOP {@code @Around DataSource.getConnection} advice (the plan's
 * pseudo-code) because Hibernate obtains connections through internal calls that bypass
 * Spring proxies; a delegating datasource intercepts reliably.
 *
 * <p>Semantics:
 * <ul>
 *   <li>tenant bound → {@code set_config('app.current_tenant_id', <uuid>, false)}.</li>
 *   <li>no tenant bound → {@code set_config('app.current_tenant_id', '', false)}, clearing
 *       any value left on the pooled connection. The RLS policies wrap the GUC in
 *       {@code NULLIF(..., '')}, so an empty value matches zero rows (fail closed).</li>
 * </ul>
 *
 * <p>Always session-level (not local) and parameterised — no string concatenation.
 */
public class TenantAwareDataSource extends DelegatingDataSource {

    public TenantAwareDataSource(DataSource targetDataSource) {
        super(targetDataSource);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return applyTenant(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return applyTenant(super.getConnection(username, password));
    }

    private Connection applyTenant(Connection connection) throws SQLException {
        UUID tenantId = TenantContext.getTenantId();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT set_config('app.current_tenant_id', ?, false)")) {
            ps.setString(1, tenantId != null ? tenantId.toString() : "");
            ps.execute();
        } catch (SQLException ex) {
            // Never leak a connection if we failed to scope it.
            connection.close();
            throw ex;
        }
        return connection;
    }
}
