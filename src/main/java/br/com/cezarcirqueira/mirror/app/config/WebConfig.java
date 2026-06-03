package br.com.cezarcirqueira.mirror.app.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final CorsProperties properties;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(properties.getAllowedOriginPatterns().toArray(String[]::new))
                .allowedMethods(properties.getAllowedMethods().toArray(String[]::new))
                .allowedHeaders(properties.getAllowedHeaders().toArray(String[]::new))
                .exposedHeaders(properties.getExposedHeaders().toArray(String[]::new))
                .allowCredentials(properties.isAllowCredentials())
                .maxAge(properties.getMaxAge());
    }
}
