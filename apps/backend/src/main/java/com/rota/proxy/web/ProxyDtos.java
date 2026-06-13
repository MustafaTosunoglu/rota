package com.rota.proxy.web;

import com.rota.proxy.jpa.TryItHistoryEntity;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/** Request/response records of the proxy module's REST API. */
public final class ProxyDtos {

    private ProxyDtos() {
    }

    public record ExecuteRequest(
            @NotNull UUID endpointId,
            @NotNull UUID environmentId,
            Map<String, String> pathParams,
            Map<String, String> queryParams,
            Map<String, String> headers,
            String body) {
    }

    public record ExecuteResponse(int status, long latencyMs, Map<String, String> headers,
                                  String body, boolean truncated) {
    }

    public record QuotaResponse(long remaining, int limit) {
    }

    public record HistoryEntryResponse(UUID id, String method, String url, Integer statusCode,
                                       Long latencyMs, OffsetDateTime executedAt,
                                       Map<String, Object> requestSummary,
                                       Map<String, Object> responseSummary) {

        public static HistoryEntryResponse from(TryItHistoryEntity e) {
            return new HistoryEntryResponse(e.getId(), e.getMethod(), e.getUrl(), e.getStatusCode(),
                    e.getLatencyMs(), e.getExecutedAt(), e.getRequestSummaryJson(),
                    e.getResponseSummaryJson());
        }
    }
}
