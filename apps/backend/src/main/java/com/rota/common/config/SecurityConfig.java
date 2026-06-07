package com.rota.common.config;

import com.rota.common.ratelimit.RateLimitFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless JWT (RS256) security (plan §8.3). Auth endpoints are public; everything else
 * requires a valid bearer access token. After authentication, {@link JwtTenantContextFilter}
 * binds the tenant context for RLS.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtDecoder jwtDecoder,
                                           JwtTenantContextFilter tenantContextFilter,
                                           ObjectProvider<RateLimitFilter> rateLimitFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // stateless bearer-token API, no cookies/sessions
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/verify-email",
                                "/api/v1/auth/verify-email/resend",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt
                        .decoder(jwtDecoder)
                        .jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .addFilterAfter(tenantContextFilter, BearerTokenAuthenticationFilter.class);
        // Throttle (per-IP) before authentication so abusive traffic is rejected cheaply.
        // Present only when rate limiting is enabled (Redis-backed); otherwise skipped.
        rateLimitFilter.ifAvailable(filter ->
                http.addFilterBefore(filter, BearerTokenAuthenticationFilter.class));
        return http.build();
    }

    /** Maps the {@code roles} claim to {@code ROLE_*} authorities for method security. */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
