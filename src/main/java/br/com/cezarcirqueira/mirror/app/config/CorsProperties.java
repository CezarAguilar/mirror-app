package br.com.cezarcirqueira.mirror.app.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "mirror-app.cors")
public class CorsProperties {

    private List<String> allowedOriginPatterns = List.of("*");

    private List<String> allowedMethods = List.of("*");

    private List<String> allowedHeaders = List.of("*");

    private List<String> exposedHeaders = List.of("Content-Disposition");

    private boolean allowCredentials = false;

    private long maxAge = 3600;
}
