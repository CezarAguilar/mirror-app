package br.com.cezarcirqueira.mirror.app.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "mirror-app.crypto")
public class CryptoProperties {

    private String keystorePath;

    private String keystoreType = "PKCS12";

    private String keystorePassword = "";

    private String keyAlias = "mirror-app";

    private String keyPassword = "";
}
