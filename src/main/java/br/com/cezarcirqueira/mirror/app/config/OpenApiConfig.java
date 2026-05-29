package br.com.cezarcirqueira.mirror.app.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI mirrorAppOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Mirror App API")
                        .description("API do projeto Mirror App")
                        .version("v0.0.1")
                        .contact(new Contact()
                                .name("Cezar Cirqueira")
                                .url("https://github.com/cezarcirqueira"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local")
                ));
    }
}
