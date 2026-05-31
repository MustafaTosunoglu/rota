package com.rota.common.encryption;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Encryption configuration. The master key (KEK) is a 32-byte (256-bit) key, base64
 * encoded. It NEVER has a committed default: dev supplies it via gitignored
 * application-local.yml, prod via a cloud secret manager (Faz 18). See [[rota-dev-decisions]].
 */
@Component
@ConfigurationProperties("rota.encryption")
public class EncryptionProperties {

    /** Base64-encoded 256-bit AES master key (KEK). */
    private String masterKey;

    public String getMasterKey() {
        return masterKey;
    }

    public void setMasterKey(String masterKey) {
        this.masterKey = masterKey;
    }
}
