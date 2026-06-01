package br.com.cezarcirqueira.mirror.app.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * External configuration for the hybrid crypto stack. When {@link #keystorePath}
 * is blank, the application falls back to an in-memory ephemeral RSA key pair —
 * useful for local development, never for production.
 */
@Data
@Component
@ConfigurationProperties(prefix = "mirror-app.crypto")
public class CryptoProperties {

    /** Spring resource location (e.g. {@code classpath:keystore.p12}, {@code file:./data/keystore.p12}). */
    private String keystorePath;

    private String keystoreType = "PKCS12";

    private String keystorePassword = "";

    private String keyAlias = "mirror-app";

    private String keyPassword = "";
}
