package com.rota.common.email;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Sends HTML email through the Resend HTTP API ({@code POST /emails}). Activated by
 * {@code rota.email.provider=resend}; the API key comes from configuration (local override or
 * env var) and is never logged — neither are Resend response bodies, only the HTTP status.
 *
 * <p>Like {@link SmtpEmailSender}, send failures are logged, not rethrown: a transactional
 * email that fails must never break the user-facing operation that triggered it.
 */
public class ResendEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailSender.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final EmailProperties properties;
    private final URI endpoint;

    public ResendEmailSender(HttpClient httpClient, ObjectMapper objectMapper,
                             EmailProperties properties, String apiBaseUrl) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.endpoint = URI.create(apiBaseUrl + "/emails");
    }

    @Override
    public void send(String to, String subject, String htmlBody) {
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + properties.getResendApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload(to, subject, htmlBody),
                            StandardCharsets.UTF_8))
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() / 100 == 2) {
                log.debug("Sent email via Resend to {} (subject: {})", to, subject);
            } else {
                log.warn("Resend rejected email to {} (subject: {}): HTTP {}",
                        to, subject, response.statusCode());
            }
        } catch (IOException ex) {
            log.warn("Failed to send email via Resend to {} (subject: {}): {}",
                    to, subject, ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while sending email via Resend to {} (subject: {})", to, subject);
        }
    }

    private String payload(String to, String subject, String htmlBody) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "from", properties.getFrom(),
                    "to", List.of(to),
                    "subject", subject,
                    "html", htmlBody));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialise Resend payload", ex);
        }
    }
}
