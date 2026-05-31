package com.rota.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * ⚠️ PHASE 0/1B PLACEHOLDER — TEMPORARY.
 *
 * <p>This permits ALL requests so the skeleton boots and {@code /actuator/health}
 * is reachable. It carries NO real authentication.
 *
 * <p>Phase 1D (task 1.7) REPLACES this entirely with the production security config:
 * JWT filter, refresh-token rotation, Argon2id password hashing, and per-endpoint
 * authorization. Do not build on top of this class — it will be deleted.
 *
 * <p>Lives in the {@code common} module so security wiring is cross-cutting infrastructure
 * rather than a stray top-level package that Spring Modulith would treat as its own module.
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
