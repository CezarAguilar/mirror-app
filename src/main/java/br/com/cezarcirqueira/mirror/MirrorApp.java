package br.com.cezarcirqueira.mirror;

import br.com.cezarcirqueira.mirror.service.AppStateService;
import br.com.cezarcirqueira.mirror.service.FileWatcherService;
import br.com.cezarcirqueira.mirror.service.FolderRegistryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;

import java.awt.Desktop;
import java.net.URI;

@SpringBootApplication
public class MirrorApp {

    private static final Logger log = LoggerFactory.getLogger(MirrorApp.class);

    private final AppStateService appStateService;
    private final FolderRegistryService folderRegistryService;
    private final FileWatcherService fileWatcherService;

    @Value("${server.port:8080}")
    private int serverPort;

    public MirrorApp(AppStateService appStateService,
                     FolderRegistryService folderRegistryService,
                     FileWatcherService fileWatcherService) {
        this.appStateService = appStateService;
        this.folderRegistryService = folderRegistryService;
        this.fileWatcherService = fileWatcherService;
    }

    public static void main(String[] args) {
        SpringApplication.run(MirrorApp.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(10)
    public void onReady() {
        String url = "http://localhost:" + serverPort;
        log.info("Application started. Opening browser at {}", url);

        if (appStateService.isMaster()) {
            folderRegistryService.listMasterFolders().forEach(f ->
                    fileWatcherService.startWatching(f.getGuid(), f.getFolderPath()));
        }

        openBrowser(url);
    }

    private void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                return;
            }
        } catch (Exception e) {
            log.debug("Desktop browse failed, trying OS command", e);
        }

        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("win")) {
                Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", url});
            } else if (os.contains("mac")) {
                Runtime.getRuntime().exec(new String[]{"open", url});
            } else {
                Runtime.getRuntime().exec(new String[]{"xdg-open", url});
            }
        } catch (Exception e) {
            log.warn("Could not open browser automatically. Please open: {}", url);
        }
    }
}
