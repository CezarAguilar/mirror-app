package br.com.cezarcirqueira.mirror.app.application.bootstrap;

import br.com.cezarcirqueira.mirror.app.application.config.MirrorProperties;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.NodeSettingsRepository;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity.NodeSettingsEntity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DataDirectoryInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataDirectoryInitializer.class);

    private final MirrorProperties mirrorProperties;
    private final NodeSettingsRepository nodeSettingsRepository;

    public DataDirectoryInitializer(MirrorProperties mirrorProperties, NodeSettingsRepository nodeSettingsRepository) {
        this.mirrorProperties = mirrorProperties;
        this.nodeSettingsRepository = nodeSettingsRepository;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Path dir = Path.of(mirrorProperties.getDataDir());
        try {
            Files.createDirectories(dir);
            log.info("Ensured data directory exists at {}", dir.toAbsolutePath());
        } catch (IOException e) {
            throw new IllegalStateException("Could not create data directory: " + dir, e);
        }

        if (nodeSettingsRepository.count() == 0) {
            NodeSettingsEntity settings = new NodeSettingsEntity(mirrorProperties.getNodeDisplayName());
            nodeSettingsRepository.save(settings);
            log.info("Initialized node display name to {}", settings.getDisplayName());
        }
    }
}
