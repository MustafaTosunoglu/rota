package com.rota.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Per-IP rate limit on the unauthenticated auth endpoints (plan §8.5). Runs before
 * authentication so abusive traffic is rejected cheaply. Buckets live in Redis (Bucket4j),
 * so the limit holds across instances. Exceeding it yields {@code 429} + {@code Retry-After}.
 */
@Component
@ConditionalOnProperty(prefix = "rota.rate-limit", name = "enabled", havingValue = "true")
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> THROTTLED_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            "/api/v1/auth/verify-email",
            "/api/v1/auth/verify-email/resend");

    private final ProxyManager<byte[]> proxyManager;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(ProxyManager<byte[]> proxyManager,
                           RateLimitProperties properties,
                           ObjectMapper objectMapper) {
        this.proxyManager = proxyManager;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !THROTTLED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        byte[] key = ("rl:auth:" + clientIp(request)).getBytes(StandardCharsets.UTF_8);
        Bucket bucket = proxyManager.builder().build(key, bucketConfiguration());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            response.setHeader("X-RateLimit-Remaining", Long.toString(probe.getRemainingTokens()));
            chain.doFilter(request, response);
            return;
        }
        writeTooManyRequests(request, response, secondsUntilRefill(probe));
    }

    private Supplier<BucketConfiguration> bucketConfiguration() {
        long capacity = properties.getCapacity();
        return () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillGreedy(capacity, properties.getRefillPeriod())
                        .build())
                .build();
    }

    private void writeTooManyRequests(HttpServletRequest request, HttpServletResponse response, long retryAfterSeconds)
            throws IOException {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.TOO_MANY_REQUESTS);
        problem.setTitle("Too many requests");
        problem.setDetail("Rate limit exceeded. Please retry in " + retryAfterSeconds + " second(s).");
        problem.setProperty("code", "rate_limited");
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }

    private static long secondsUntilRefill(ConsumptionProbe probe) {
        return Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L);
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr();
    }
}
