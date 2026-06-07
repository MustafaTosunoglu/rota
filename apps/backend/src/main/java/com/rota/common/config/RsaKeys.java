package com.rota.common.config;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** Parses PEM-encoded RSA keys. */
final class RsaKeys {

    private RsaKeys() {
    }

    static RSAPrivateKey parsePrivate(String pem) {
        byte[] der = derBytes(pem, "PRIVATE KEY");
        try {
            return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Invalid RSA private key (expected PKCS#8 PEM)", e);
        }
    }

    static RSAPublicKey parsePublic(String pem) {
        byte[] der = derBytes(pem, "PUBLIC KEY");
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(der));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Invalid RSA public key (expected X.509 PEM)", e);
        }
    }

    private static byte[] derBytes(String pem, String type) {
        String base64 = pem
                .replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }
}
