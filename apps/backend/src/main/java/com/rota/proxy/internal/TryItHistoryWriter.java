package com.rota.proxy.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rota.proxy.jpa.TryItHistoryEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Inserts Try It history rows via JDBC (always an INSERT; the table is append-only and grants
 * no UPDATE). Direct JDBC avoids JPA's assigned-id merge/UPDATE ambiguity and matches the
 * audit log's writer. Runs in its own short transaction; RLS is scoped by the current tenant.
 */
@Component
class TryItHistoryWriter {

    private static final String INSERT_SQL = """
            INSERT INTO try_it_history
                (id, tenant_id, user_id, endpoint_id, executed_at, method, url, status_code,
                 latency_ms, request_summary_json, response_summary_json)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb))
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    TryItHistoryWriter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void save(TryItHistoryEntity e) {
        jdbcTemplate.update(INSERT_SQL,
                e.getId(), e.getTenantId(), e.getUserId(), e.getEndpointId(), e.getExecutedAt(),
                e.getMethod(), e.getUrl(), e.getStatusCode(), e.getLatencyMs(),
                toJson(e.getRequestSummaryJson()), toJson(e.getResponseSummaryJson()));
    }

    private String toJson(Map<String, Object> value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialise Try It history summary", ex);
        }
    }
}
