package com.rota.proxy.internal;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

/** Builds the absolute target URL from an environment base URL, endpoint path and params. */
@Component
public class TargetUrlBuilder {

    /**
     * {@code baseUrl} + {@code pathTemplate} (with {@code {var}} substituted, URL-encoded) +
     * encoded query string. Throws {@link SsrfBlockedException} if the result is not a URI.
     */
    public URI build(String baseUrl, String pathTemplate, Map<String, String> pathParams,
                     Map<String, String> queryParams) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String path = substitutePathParams(pathTemplate == null ? "" : pathTemplate, pathParams);
        if (!path.isEmpty() && !path.startsWith("/")) {
            path = "/" + path;
        }
        String query = buildQuery(queryParams);
        String full = base + path + (query.isEmpty() ? "" : "?" + query);
        try {
            return URI.create(full);
        } catch (IllegalArgumentException e) {
            throw new SsrfBlockedException("Invalid target URL: " + full);
        }
    }

    private String substitutePathParams(String pathTemplate, Map<String, String> pathParams) {
        String path = pathTemplate;
        if (pathParams != null) {
            for (Map.Entry<String, String> entry : pathParams.entrySet()) {
                String encoded = URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8)
                        .replace("+", "%20");
                path = path.replace("{" + entry.getKey() + "}", encoded);
            }
        }
        return path;
    }

    private String buildQuery(Map<String, String> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return "";
        }
        return queryParams.entrySet().stream()
                .filter(e -> e.getKey() != null && !e.getKey().isBlank())
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue() == null ? "" : e.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
