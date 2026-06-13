package com.rota.proxy.internal;

import com.rota.common.security.CurrentUser;
import com.rota.common.tenant.TenantContext;
import com.rota.documents.api.EnvironmentResolver;
import com.rota.documents.api.EnvironmentResolver.EnvironmentTarget;
import com.rota.endpoints.api.EndpointResolver;
import com.rota.endpoints.api.EndpointResolver.EndpointTarget;
import com.rota.proxy.jpa.TryItHistoryEntity;
import com.rota.proxy.jpa.TryItHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Executes a Try It request (plan §13.1 Mode A). Validates the target (endpoint/environment +
 * SSRF), enforces the daily quota, calls the target API with timeouts and a response-size cap,
 * records a redacted history row and returns the response. The outbound call runs outside any
 * DB transaction; the history row is saved afterwards (RLS-scoped to the current tenant).
 */
@Service
public class ProxyService {

    private static final Logger log = LoggerFactory.getLogger(ProxyService.class);
    private static final int PREVIEW_LIMIT = 4096;

    /** Headers the JDK HttpClient forbids callers from setting, or that must not be overridden. */
    private static final Set<String> RESTRICTED_HEADERS = Set.of(
            "host", "content-length", "connection", "upgrade", "transfer-encoding", "expect", "keep-alive");

    private final EndpointResolver endpointResolver;
    private final EnvironmentResolver environmentResolver;
    private final HttpClient httpClient;
    private final SsrfGuard ssrfGuard;
    private final RedactionService redaction;
    private final TargetUrlBuilder urlBuilder;
    private final TryItQuota quota;
    private final TryItHistoryRepository history;
    private final TryItHistoryWriter historyWriter;
    private final ProxyProperties properties;

    public ProxyService(EndpointResolver endpointResolver,
                        EnvironmentResolver environmentResolver,
                        HttpClient proxyHttpClient,
                        SsrfGuard ssrfGuard,
                        RedactionService redaction,
                        TargetUrlBuilder urlBuilder,
                        TryItQuota quota,
                        TryItHistoryRepository history,
                        TryItHistoryWriter historyWriter,
                        ProxyProperties properties) {
        this.endpointResolver = endpointResolver;
        this.environmentResolver = environmentResolver;
        this.httpClient = proxyHttpClient;
        this.ssrfGuard = ssrfGuard;
        this.redaction = redaction;
        this.urlBuilder = urlBuilder;
        this.quota = quota;
        this.history = history;
        this.historyWriter = historyWriter;
        this.properties = properties;
    }

    public record ExecuteCommand(UUID endpointId, UUID environmentId, Map<String, String> pathParams,
                                 Map<String, String> queryParams, Map<String, String> headers, String body) {
    }

    public record ExecuteResult(int status, long latencyMs, Map<String, String> headers, String body,
                                boolean truncated) {
    }

    public ExecuteResult execute(ExecuteCommand command) {
        EndpointTarget endpoint = endpointResolver.resolve(command.endpointId());
        EnvironmentTarget environment = environmentResolver.resolve(command.environmentId());
        if (!environment.documentVersionId().equals(endpoint.documentVersionId())) {
            throw new ProxyBadRequestException("Environment does not belong to this endpoint's version.");
        }

        URI target = urlBuilder.build(environment.baseUrl(), endpoint.path(),
                command.pathParams(), command.queryParams());
        ssrfGuard.validate(target); // throws SsrfBlockedException → 400

        // Only count quota for requests that are valid enough to actually go out.
        quota.consume(TenantContext.getTenantId()); // throws QuotaExceededException → 429

        Map<String, String> requestHeaders = command.headers() != null ? command.headers() : Map.of();
        log.info("Try It {} {} headers={}", endpoint.method(), target, redaction.redact(requestHeaders));

        HttpRequest request = buildRequest(target, endpoint.method(), requestHeaders, command.body());

        long startNanos = System.nanoTime();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            boolean[] truncated = {false};
            byte[] bytes = readCapped(response.body(), properties.getMaxResponseBytes(), truncated);
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
            String body = new String(bytes, StandardCharsets.UTF_8);
            Map<String, String> responseHeaders = firstValues(response.headers().map());

            record(endpoint, target, response.statusCode(), latencyMs, command, requestHeaders,
                    responseHeaders, body, truncated[0]);
            return new ExecuteResult(response.statusCode(), latencyMs, responseHeaders, body, truncated[0]);
        } catch (IOException e) {
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
            recordFailure(endpoint, target, latencyMs, command, requestHeaders, e.getMessage());
            throw new ProxyExecutionException("Could not reach the target API: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProxyExecutionException("The Try It request was interrupted.");
        }
    }

    public long remainingQuota() {
        return quota.remaining(TenantContext.getTenantId());
    }

    public int dailyLimit() {
        return properties.getDailyFreeLimit();
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public java.util.List<TryItHistoryEntity> recentHistory(UUID endpointId, int limit) {
        return history.findAllByEndpointIdOrderByExecutedAtDesc(endpointId,
                org.springframework.data.domain.Limit.of(limit));
    }

    private HttpRequest buildRequest(URI target, String method, Map<String, String> headers, String body) {
        HttpRequest.BodyPublisher publisher = body != null && !body.isEmpty()
                ? HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)
                : HttpRequest.BodyPublishers.noBody();
        HttpRequest.Builder builder = HttpRequest.newBuilder(target)
                .timeout(properties.getTotalTimeout())
                .method(method.toUpperCase(Locale.ROOT), publisher);
        headers.forEach((name, value) -> {
            if (value != null && !RESTRICTED_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                try {
                    builder.header(name, value);
                } catch (IllegalArgumentException ignored) {
                    // JDK rejects a few more restricted headers depending on the runtime — skip.
                }
            }
        });
        return builder.build();
    }

    private byte[] readCapped(InputStream in, long max, boolean[] truncated) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        long total = 0;
        int read;
        try (in) {
            while ((read = in.read(chunk)) != -1) {
                if (total + read > max) {
                    buffer.write(chunk, 0, (int) (max - total));
                    truncated[0] = true;
                    break;
                }
                buffer.write(chunk, 0, read);
                total += read;
            }
        }
        return buffer.toByteArray();
    }

    private Map<String, String> firstValues(Map<String, java.util.List<String>> headers) {
        Map<String, String> result = new LinkedHashMap<>();
        headers.forEach((name, values) -> {
            if (!values.isEmpty()) {
                result.put(name, values.get(0));
            }
        });
        return result;
    }

    private void record(EndpointTarget endpoint, URI target, int status, long latencyMs,
                        ExecuteCommand command, Map<String, String> requestHeaders,
                        Map<String, String> responseHeaders, String body, boolean truncated) {
        TryItHistoryEntity entity = baseEntity(endpoint, target, command, requestHeaders);
        entity.setStatusCode(status);
        entity.setLatencyMs(latencyMs);
        Map<String, Object> responseSummary = new LinkedHashMap<>();
        responseSummary.put("status", status);
        responseSummary.put("latencyMs", latencyMs);
        responseSummary.put("headers", redaction.redact(responseHeaders));
        responseSummary.put("bodyPreview", preview(body));
        responseSummary.put("truncated", truncated);
        entity.setResponseSummaryJson(responseSummary);
        historyWriter.save(entity);
    }

    private void recordFailure(EndpointTarget endpoint, URI target, long latencyMs,
                               ExecuteCommand command, Map<String, String> requestHeaders, String error) {
        TryItHistoryEntity entity = baseEntity(endpoint, target, command, requestHeaders);
        entity.setLatencyMs(latencyMs);
        Map<String, Object> responseSummary = new LinkedHashMap<>();
        responseSummary.put("error", error);
        entity.setResponseSummaryJson(responseSummary);
        historyWriter.save(entity);
    }

    private TryItHistoryEntity baseEntity(EndpointTarget endpoint, URI target, ExecuteCommand command,
                                          Map<String, String> requestHeaders) {
        TryItHistoryEntity entity = new TryItHistoryEntity();
        entity.setTenantId(TenantContext.getTenantId());
        entity.setUserId(CurrentUser.requireId());
        entity.setEndpointId(endpoint.endpointId());
        entity.setMethod(endpoint.method());
        entity.setUrl(target.toString());
        Map<String, Object> requestSummary = new LinkedHashMap<>();
        requestSummary.put("pathParams", command.pathParams());
        requestSummary.put("queryParams", command.queryParams());
        requestSummary.put("headers", redaction.redact(requestHeaders));
        requestSummary.put("bodyPreview", preview(command.body()));
        entity.setRequestSummaryJson(requestSummary);
        return entity;
    }

    private String preview(String body) {
        if (body == null) {
            return null;
        }
        return body.length() <= PREVIEW_LIMIT ? body : body.substring(0, PREVIEW_LIMIT) + "…";
    }
}
