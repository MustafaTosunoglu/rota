package com.rota.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Password hashing for the whole application: Argon2id (plan §8.2) via Spring Security's
 * {@link Argon2PasswordEncoder} (backed by Bouncy Castle). The {@code v5_8} defaults use
 * the Argon2id variant with sensible memory/iteration/parallelism parameters.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
}
