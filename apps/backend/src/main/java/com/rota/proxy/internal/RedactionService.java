package com.rota.proxy.internal;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Redacts sensitive header values for logging / history (plan §13.1). Both an explicit set of
 * well-known secret-bearing headers and a name pattern (token/secret/key/password/auth) are
 * masked to {@code [REDACTED]}.
 */
@Component
public class RedactionService {

    public static final String REDACTED = "[REDACTED]";

    private static final Set<String> SENSITIVE = Set.of(
            "authorization", "proxy-authorization", "cookie", "set-cookie", "x-api-key", "api-key");

    private static final Pattern SENSITIVE_NAME =
            Pattern.compile(".*(token|secret|password|api[-_]?key|auth|credential).*", Pattern.CASE_INSENSITIVE);

    public boolean isSensitive(String headerName) {
        String lower = headerName.toLowerCase(Locale.ROOT);
        return SENSITIVE.contains(lower) || SENSITIVE_NAME.matcher(lower).matches();
    }

    /** Returns a copy of the headers with sensitive values masked. */
    public Map<String, String> redact(Map<String, String> headers) {
        Map<String, String> result = new LinkedHashMap<>();
        if (headers == null) {
            return result;
        }
        headers.forEach((name, value) -> result.put(name, isSensitive(name) ? REDACTED : value));
        return result;
    }
}
