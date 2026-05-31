package com.rota.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * ⚠️ PHASE 0 PLACEHOLDER — TEMPORARY.
 *
 * <p>This permits ALL requests so the skeleton boots and {@code /actuator/health}
 * is reachable for the Phase 0 acceptance check. It carries NO real authentication.
 *
 * <p>Phase 1 (task 1.7) REPLACES this entirely with the production security config:
 * JWT filter, refresh-token rotation, Argon2id password hashing, and per-endpoint
 * authorization. Do not build on top of this class — it will be deleted.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
