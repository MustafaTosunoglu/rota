package com.rota.importer.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rota.endpoints.api.ImportModel.ImportEndpoint;
import com.rota.endpoints.api.ImportModel.ImportParameter;
import com.rota.endpoints.api.ImportModel.ImportRequestBody;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses a single {@code curl} command into one endpoint (plan §13.6, backend per task 4.3).
 * Covers the common shapes: {@code -X/--request}, {@code -H/--header}, {@code -d/--data*},
 * {@code -u} (basic auth), with line continuations and single/double quoting.
 */
@Component
class CurlParser {

    private final ObjectMapper objectMapper;

    CurlParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    ParsedImport parse(String content) {
        List<String> tokens = tokenize(content.replace("\\\n", " ").replace("\\\r\n", " "));
        if (tokens.isEmpty() || !tokens.get(0).equalsIgnoreCase("curl")) {
            throw new ImportParseException("Not a curl command (must start with 'curl').");
        }

        String method = null;
        String url = null;
        String basicAuth = null;
        String dataBody = null;
        List<ImportParameter> headers = new ArrayList<>();

        for (int i = 1; i < tokens.size(); i++) {
            String token = tokens.get(i);
            switch (token) {
                case "-X", "--request" -> method = next(tokens, ++i);
                case "-H", "--header" -> addHeader(next(tokens, ++i), headers);
                case "-d", "--data", "--data-raw", "--data-binary", "--data-ascii" ->
                        dataBody = append(dataBody, next(tokens, ++i));
                case "-u", "--user" -> basicAuth = next(tokens, ++i);
                case "--url" -> url = next(tokens, ++i);
                default -> {
                    if (!token.startsWith("-") && looksLikeUrl(token) && url == null) {
                        url = token;
                    }
                    // Unknown flags (e.g. -i, -s, --compressed) are ignored.
                }
            }
        }

        if (url == null) {
            throw new ImportParseException("No URL found in the curl command.");
        }
        if (method == null) {
            method = dataBody != null ? "POST" : "GET";
        }

        List<ImportParameter> parameters = new ArrayList<>();
        String path = extractPath(url, parameters);
        parameters.addAll(headers);

        String authType = basicAuth != null ? "basic"
                : hasBearer(headers) ? "bearer" : "none";

        List<ImportRequestBody> bodies = buildBody(dataBody, headers);

        ImportEndpoint endpoint = new ImportEndpoint(method.toUpperCase(Locale.ROOT), path, null, null,
                authType, null, null, parameters, bodies, List.of());
        return new ParsedImport(null, List.of(), List.of(), List.of(endpoint));
    }

    /**
     * Splits on whitespace while respecting single/double quotes. Backslash escapes a
     * character outside quotes and inside double quotes (so {@code "{\"q\": 2}"} keeps its
     * inner quotes); single quotes are literal, matching POSIX shell word-splitting.
     */
    static List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        boolean inToken = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\\' && quote != '\'' && i + 1 < input.length()) {
                current.append(input.charAt(++i)); // escaped char taken literally
                inToken = true;
            } else if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                } else {
                    current.append(c);
                }
            } else if (c == '\'' || c == '"') {
                quote = c;
                inToken = true;
            } else if (Character.isWhitespace(c)) {
                if (inToken) {
                    tokens.add(current.toString());
                    current.setLength(0);
                    inToken = false;
                }
            } else {
                current.append(c);
                inToken = true;
            }
        }
        if (inToken) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private void addHeader(String raw, List<ImportParameter> headers) {
        int colon = raw.indexOf(':');
        if (colon <= 0) {
            return;
        }
        String name = raw.substring(0, colon).trim();
        String value = raw.substring(colon + 1).trim();
        headers.add(new ImportParameter(name, "header", "string", false, null, value));
    }

    private boolean hasBearer(List<ImportParameter> headers) {
        return headers.stream().anyMatch(h ->
                "authorization".equalsIgnoreCase(h.name())
                        && h.example() != null && h.example().toLowerCase(Locale.ROOT).startsWith("bearer "));
    }

    private List<ImportRequestBody> buildBody(String dataBody, List<ImportParameter> headers) {
        if (dataBody == null || dataBody.isBlank()) {
            return List.of();
        }
        String contentType = headers.stream()
                .filter(h -> "content-type".equalsIgnoreCase(h.name()))
                .map(ImportParameter::example)
                .findFirst()
                .orElse("application/json");
        Map<String, Object> example = tryParseJsonObject(dataBody);
        List<ImportRequestBody> bodies = new ArrayList<>();
        bodies.add(new ImportRequestBody(contentType, null, example));
        return bodies;
    }

    private String extractPath(String url, List<ImportParameter> parameters) {
        try {
            URI uri = URI.create(url);
            String path = uri.getRawPath();
            if (path == null || path.isBlank()) {
                path = "/";
            }
            if (uri.getRawQuery() != null) {
                for (String pair : uri.getRawQuery().split("&")) {
                    String key = pair.split("=", 2)[0];
                    if (!key.isBlank()) {
                        String value = pair.contains("=") ? pair.split("=", 2)[1] : null;
                        parameters.add(new ImportParameter(key, "query", "string", false, null, value));
                    }
                }
            }
            return path;
        } catch (IllegalArgumentException e) {
            // Not a strict URI (maybe contains template vars) — best-effort path slice.
            String withoutQuery = url.split("\\?", 2)[0];
            int schemeIdx = withoutQuery.indexOf("://");
            if (schemeIdx >= 0) {
                int slash = withoutQuery.indexOf('/', schemeIdx + 3);
                return slash >= 0 ? withoutQuery.substring(slash) : "/";
            }
            return withoutQuery.startsWith("/") ? withoutQuery : "/" + withoutQuery;
        }
    }

    private Map<String, Object> tryParseJsonObject(String raw) {
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node != null && node.isObject()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = objectMapper.convertValue(node, LinkedHashMap.class);
                return map;
            }
        } catch (Exception ignored) {
            // Non-JSON body — keep the endpoint without a structured example.
        }
        return null;
    }

    private boolean looksLikeUrl(String token) {
        return token.contains("://") || token.startsWith("www.") || token.matches("[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}.*");
    }

    private String next(List<String> tokens, int index) {
        if (index >= tokens.size()) {
            throw new ImportParseException("Malformed curl: missing value for a flag.");
        }
        return tokens.get(index);
    }

    private String append(String existing, String more) {
        return existing == null ? more : existing + "&" + more;
    }
}
