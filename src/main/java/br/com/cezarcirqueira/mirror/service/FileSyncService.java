package br.com.cezarcirqueira.mirror.service;

import br.com.cezarcirqueira.mirror.domain.FolderMapping;
import br.com.cezarcirqueira.mirror.domain.SyncFolder;
import br.com.cezarcirqueira.mirror.websocket.MasterWebSocketHandler;
import br.com.cezarcirqueira.mirror.websocket.SlaveWebSocketClient;
import br.com.cezarcirqueira.mirror.websocket.SyncMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.nio.file.*;
import java.util.Optional;

@Service
public class FileSyncService {

    private static final Logger log = LoggerFactory.getLogger(FileSyncService.class);

    private final AppStateService appStateService;
    private final FolderRegistryService folderRegistryService;
    private final NetworkInfoService networkInfoService;
    private final MasterWebSocketHandler masterWsHandler;
    private final SlaveWebSocketClient slaveWsClient;
    private final RestTemplate restTemplate;

    @Value("${server.port:8080}")
    private int serverPort;

    public FileSyncService(AppStateService appStateService,
                           FolderRegistryService folderRegistryService,
                           NetworkInfoService networkInfoService,
                           MasterWebSocketHandler masterWsHandler,
                           SlaveWebSocketClient slaveWsClient,
                           RestTemplate restTemplate) {
        this.appStateService = appStateService;
        this.folderRegistryService = folderRegistryService;
        this.networkInfoService = networkInfoService;
        this.masterWsHandler = masterWsHandler;
        this.slaveWsClient = slaveWsClient;
        this.restTemplate = restTemplate;
    }

    public void handleLocalChange(String guid, String relativePath, String action, String folderPath) {
        String selfIp = networkInfoService.getLocalIpV4();
        SyncMessage message = new SyncMessage(guid, relativePath, action, selfIp);

        if (appStateService.isMaster()) {
            masterWsHandler.broadcast(message);
            log.info("Master broadcast: {} {} guid={}", action, relativePath, guid);
        } else if (appStateService.isSlave()) {
            if ("DELETE".equals(action)) {
                slaveWsClient.send(message);
            } else {
                uploadToMaster(guid, relativePath, folderPath);
                slaveWsClient.send(message);
            }
        }
    }

    public void handleIncomingMasterMessage(SyncMessage message, WebSocketSession sourceSession) {
        String selfIp = networkInfoService.getLocalIpV4();
        if (selfIp.equals(message.getOriginIp())) {
            log.debug("Echo cancellation: ignoring own message");
            return;
        }

        Optional<SyncFolder> folder = folderRegistryService.findMasterFolderByGuid(message.getGuid());
        if (folder.isEmpty()) {
            log.warn("Unknown guid in WS message: {}", message.getGuid());
            return;
        }

        String folderPath = folder.get().getFolderPath();
        if ("DELETE".equals(message.getAction())) {
            deleteLocalFile(folderPath, message.getRelativePath());
        } else {
            downloadFromPeer(message.getOriginIp(), message.getGuid(), message.getRelativePath(), folderPath);
        }

        SyncMessage rebroadcast = new SyncMessage(message.getGuid(), message.getRelativePath(), message.getAction(), selfIp);
        masterWsHandler.broadcast(rebroadcast, sourceSession);
    }

    public void handleIncomingSlaveMessage(SyncMessage message) {
        String selfIp = networkInfoService.getLocalIpV4();
        if (selfIp.equals(message.getOriginIp())) {
            log.debug("Echo cancellation: ignoring own message");
            return;
        }

        Optional<FolderMapping> mapping = folderRegistryService.findMappingByGuid(message.getGuid());
        if (mapping.isEmpty() || mapping.get().getLocalPath() == null) {
            log.debug("No local mapping for guid {}, skipping", message.getGuid());
            return;
        }

        String localPath = mapping.get().getLocalPath();
        if ("DELETE".equals(message.getAction())) {
            deleteLocalFile(localPath, message.getRelativePath());
        } else {
            var settings = appStateService.getSettings();
            downloadFromPeer(settings.getMasterIp(), message.getGuid(), message.getRelativePath(), localPath);
        }
    }

    private void uploadToMaster(String guid, String relativePath, String localFolderPath) {
        var settings = appStateService.getSettings();
        if (settings.getMasterIp() == null) return;

        Path file = Paths.get(localFolderPath, relativePath.replace("/", FileSystems.getDefault().getSeparator()));
        if (!Files.exists(file)) return;

        String url = "http://" + settings.getMasterIp() + ":" + settings.getMasterPort() + "/api/files/upload";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("guid", guid);
            body.add("relativePath", relativePath);
            body.add("file", new FileSystemResource(file));

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.exchange(url, HttpMethod.POST, request, Void.class);
            log.info("Uploaded {} to master", relativePath);
        } catch (Exception e) {
            log.error("Failed to upload {} to master: {}", relativePath, e.getMessage());
        }
    }

    private void downloadFromPeer(String peerIp, String guid, String relativePath, String localFolderPath) {
        int port = resolvePort(peerIp);
        try {
            java.net.URI uri = UriComponentsBuilder
                    .fromHttpUrl("http://" + peerIp + ":" + port + "/api/files/download")
                    .queryParam("guid", guid)
                    .queryParam("path", relativePath)
                    .build()
                    .encode()
                    .toUri();
            ResponseEntity<byte[]> response = restTemplate.getForEntity(uri, byte[].class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Path target = Paths.get(localFolderPath, relativePath.replace("/", FileSystems.getDefault().getSeparator()));
                Files.createDirectories(target.getParent());
                Files.write(target, response.getBody(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                log.info("Downloaded {} from {} to {}", relativePath, peerIp, target);
            }
        } catch (Exception e) {
            log.error("Failed to download {} from {}: {}", relativePath, peerIp, e.getMessage());
        }
    }

    private void deleteLocalFile(String folderPath, String relativePath) {
        try {
            Path target = Paths.get(folderPath, relativePath.replace("/", FileSystems.getDefault().getSeparator()));
            Files.deleteIfExists(target);
            log.info("Deleted local file: {}", target);
        } catch (IOException e) {
            log.error("Failed to delete {}: {}", relativePath, e.getMessage());
        }
    }

    private int resolvePort(String ip) {
        var settings = appStateService.getSettings();
        if (settings.getMasterIp() != null && settings.getMasterIp().equals(ip)) {
            return settings.getMasterPort() != null ? settings.getMasterPort() : 8080;
        }
        return serverPort;
    }

}
