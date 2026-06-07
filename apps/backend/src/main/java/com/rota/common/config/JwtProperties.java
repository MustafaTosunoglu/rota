package com.rota.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** RS256 JWT settings (keys, issuer, token lifetimes). Keys are optional — see {@link JwtConfig}. */
@Component
@ConfigurationProperties("rota.jwt")
public class JwtProperties {

    /** RSA private key, PEM (PKCS#8). Blank => ephemeral keypair generated at startup. */
    private String privateKey;
    /** RSA public key, PEM (X.509 SubjectPublicKeyInfo). Blank => ephemeral keypair. */
    private String publicKey;
    private String issuer = "rota";
    private Duration accessTokenTtl = Duration.ofMinutes(15);
    private Duration refreshTokenTtl = Duration.ofDays(30);

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    public void setRefreshTokenTtl(Duration refreshTokenTtl) {
        this.refreshTokenTtl = refreshTokenTtl;
    }
}
