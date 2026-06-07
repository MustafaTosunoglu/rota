package com.rota.common.config;

import com.rota.common.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * After the bearer token is authenticated, binds {@link TenantContext} to the {@code tenant_id}
 * claim so RLS applies to every query in the request. ALWAYS clears the context at the end of
 * the request to avoid leaking a tenant onto a pooled (platform) thread.
 *
 * <p>Unauthenticated endpoints (register/login/refresh) carry no JWT here; those flows manage
 * their own tenant context internally.
 */
@Component
public class JwtTenantContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken jwtAuth) {
                String tenantId = jwtAuth.getToken().getClaimAsString("tenant_id");
                if (StringUtils.hasText(tenantId)) {
                    try {
                        TenantContext.setTenantId(UUID.fromString(tenantId));
                    } catch (IllegalArgumentException ignored) {
                        // Malformed claim => leave context unbound (fail closed).
                    }
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
