package br.com.cezarcirqueira.mirror.app;

import org.springframework.boot.SpringApplication;
import br.com.cezarcirqueira.mirror.app.application.config.MirrorProperties;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(MirrorProperties.class)
public class MirrorAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(MirrorAppApplication.class, args);
    }
}
