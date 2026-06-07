package com.rota.common.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.util.StringUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * RS256 JWT encoder/decoder backed by an {@link RSAKey}. The key is loaded from configured
 * PEM if present; otherwise an EPHEMERAL keypair is generated (dev/test convenience — logged
 * loudly). Production must configure stable keys.
 */
@Configuration
public class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    private final JwtProperties properties;

    public JwtConfig(JwtProperties properties) {
        this.properties = properties;
    }

    @Bean
    RSAKey jwtRsaKey() {
        if (StringUtils.hasText(properties.getPrivateKey()) && StringUtils.hasText(properties.getPublicKey())) {
            RSAPublicKey publicKey = RsaKeys.parsePublic(properties.getPublicKey());
            RSAPrivateKey privateKey = RsaKeys.parsePrivate(properties.getPrivateKey());
            return new RSAKey.Builder(publicKey).privateKey(privateKey).keyID("rota-jwt").build();
        }
        log.warn("rota.jwt.private-key/public-key not configured — generating an EPHEMERAL RS256 "
                + "keypair. Tokens will be invalidated on restart. DO NOT run production like this.");
        KeyPair keyPair = generateEphemeralKeyPair();
        return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID("rota-jwt-ephemeral")
                .build();
    }

    @Bean
    JwtEncoder jwtEncoder(RSAKey jwtRsaKey) {
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(jwtRsaKey));
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    JwtDecoder jwtDecoder(RSAKey jwtRsaKey) throws JOSEException {
        return NimbusJwtDecoder.withPublicKey(jwtRsaKey.toRSAPublicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
    }

    private static KeyPair generateEphemeralKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA key generation unavailable", e);
        }
    }
}
