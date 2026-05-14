package br.com.cezarcirqueira.mirror.service;

import br.com.cezarcirqueira.mirror.domain.AppSettings;
import br.com.cezarcirqueira.mirror.domain.SyncFolder;
import br.com.cezarcirqueira.mirror.websocket.SlaveWebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class SlaveReconnectionService {

    private static final Logger log = LoggerFactory.getLogger(SlaveReconnectionService.class);

    private final AppStateService appStateService;
    private final FolderRegistryService folderRegistryService;
    private final NetworkInfoService networkInfoService;
    private final FileWatcherService fileWatcherService;
    private final SlaveWebSocketClient slaveWsClient;
    private final RestTemplate restTemplate;

    public SlaveReconnectionService(AppStateService appStateService,
                                     FolderRegistryService folderRegistryService,
                                     NetworkInfoService networkInfoService,
                                     FileWatcherService fileWatcherService,
                                     SlaveWebSocketClient slaveWsClient,
                                     RestTemplate restTemplate) {
        this.appStateService = appStateService;
        this.folderRegistryService = folderRegistryService;
        this.networkInfoService = networkInfoService;
        this.fileWatcherService = fileWatcherService;
        this.slaveWsClient = slaveWsClient;
        this.restTemplate = restTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (!appStateService.isSlave()) return;
        log.info("Slave startup: reconnecting to master");
        reconnect();
    }

    public void reconnect() {
        AppSettings settings = appStateService.getSettings();
        if (settings.getMasterIp() == null) return;

        String masterBase = "http://" + settings.getMasterIp() + ":" + settings.getMasterPort();

        registerWithMaster(masterBase);
        refreshFolderMappings(masterBase);
        slaveWsClient.connect(settings.getMasterIp(), settings.getMasterPort());
        startWatchingMappedFolders();
        performStateRecovery(masterBase);
    }

    private void registerWithMaster(String masterBase) {
        try {
            Map<String, Object> body = Map.of(
                    "name", networkInfoService.getHostName(),
                    "ip", networkInfoService.getLocalIpV4(),
                    "port", appStateService.getSettings().getMasterPort() == null ? 8080
                            : getCurrentServerPort()
            );
            restTemplate.postForObject(masterBase + "/api/peer/register", body, Void.class);
            log.info("Registered with master at {}", masterBase);
        } catch (Exception e) {
            log.warn("Failed to register with master: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void refreshFolderMappings(String masterBase) {
        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    masterBase + "/api/peer/folders",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );
            if (response.getBody() != null) {
                for (Map<String, Object> folder : response.getBody()) {
                    String guid = (String) folder.get("guid");
                    String path = (String) folder.get("folderPath");
                    folderRegistryService.upsertMapping(guid, path);
                }
                log.info("Refreshed {} folder mappings from master", response.getBody().size());
            }
        } catch (Exception e) {
            log.warn("Failed to refresh folders from master: {}", e.getMessage());
        }
    }

    private void startWatchingMappedFolders() {
        folderRegistryService.listActiveMappings().forEach(m -> {
            if (m.getLocalPath() != null) {
                fileWatcherService.startWatching(m.getGuid(), m.getLocalPath());
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void performStateRecovery(String masterBase) {
        folderRegistryService.listActiveMappings().forEach(mapping -> {
            try {
                ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                        masterBase + "/api/sync/state?guid=" + mapping.getGuid(),
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<>() {}
                );
                if (response.getBody() != null) {
                    for (Map<String, Object> entry : response.getBody()) {
                        String relativePath = (String) entry.get("relativePath");
                        String remoteHash = (String) entry.get("hash");

                        java.nio.file.Path localFile = java.nio.file.Paths.get(
                                mapping.getLocalPath(),
                                relativePath.replace("/", java.nio.file.FileSystems.getDefault().getSeparator())
                        );

                        String localHash = computeLocalHash(localFile);
                        if (!remoteHash.equals(localHash)) {
                            downloadMissingFile(masterBase, mapping.getGuid(), relativePath, mapping.getLocalPath());
                        }
                    }
                    log.info("State recovery complete for guid={}", mapping.getGuid());
                }
            } catch (Exception e) {
                log.warn("State recovery failed for guid={}: {}", mapping.getGuid(), e.getMessage());
            }
        });
    }

    private String computeLocalHash(java.nio.file.Path file) {
        if (!java.nio.file.Files.exists(file)) return "";
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(file);
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private void downloadMissingFile(String masterBase, String guid, String relativePath, String localFolder) {
        try {
            byte[] data = restTemplate.getForObject(
                    masterBase + "/api/files/download?guid=" + guid + "&path=" + java.net.URLEncoder.encode(relativePath, "UTF-8"),
                    byte[].class
            );
            if (data != null) {
                java.nio.file.Path target = java.nio.file.Paths.get(localFolder,
                        relativePath.replace("/", java.nio.file.FileSystems.getDefault().getSeparator()));
                java.nio.file.Files.createDirectories(target.getParent());
                java.nio.file.Files.write(target, data,
                        java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
                log.info("Recovered file: {}", relativePath);
            }
        } catch (Exception e) {
            log.warn("Failed to recover file {}: {}", relativePath, e.getMessage());
        }
    }

    private int getCurrentServerPort() {
        try {
            String portProp = System.getProperty("local.server.port");
            if (portProp != null) return Integer.parseInt(portProp);
        } catch (Exception ignored) {}
        return 8080;
    }
}
