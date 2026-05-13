package br.com.cezarcirqueira.mirror.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Ensures the SQLite parent directory exists before the datasource is initialized.
 */
public class MirrorApplicationContextInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment env = applicationContext.getEnvironment();
        String dataDir = env.getProperty("mirror.data-dir");
        if (dataDir == null || dataDir.isBlank()) {
            dataDir = Path.of(System.getProperty("user.home"), ".mirror-app").toString();
        }
        try {
            Files.createDirectories(Path.of(dataDir));
        } catch (IOException e) {
            throw new IllegalStateException("Could not create mirror data directory: " + dataDir, e);
        }
    }
}
