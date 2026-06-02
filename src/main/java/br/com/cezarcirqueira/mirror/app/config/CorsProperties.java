package br.com.cezarcirqueira.mirror.app.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Cross-Origin Resource Sharing settings applied to every REST endpoint.
 *
 * <p>Defaults are intentionally permissive to ease LAN/dev usage. In
 * production, restrict {@link #allowedOriginPatterns} to the known clients
 * and consider flipping {@link #allowCredentials} only if cookies/session
 * are involved.</p>
 */
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
