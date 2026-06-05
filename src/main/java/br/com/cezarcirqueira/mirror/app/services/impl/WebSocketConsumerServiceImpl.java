package br.com.cezarcirqueira.mirror.app.services.impl;

import br.com.cezarcirqueira.mirror.app.model.ConnectionMode;
import br.com.cezarcirqueira.mirror.app.model.SyncFolder;
import br.com.cezarcirqueira.mirror.app.model.dto.ConsumerStatusResponse;
import br.com.cezarcirqueira.mirror.app.model.dto.WebSocketMessagePayload;
import br.com.cezarcirqueira.mirror.app.model.dto.sync.FileSyncMessage;
import br.com.cezarcirqueira.mirror.app.repositories.SyncFolderRepository;
import br.com.cezarcirqueira.mirror.app.services.WebSocketConsumerService;
import br.com.cezarcirqueira.mirror.app.sync.InstanceIdentityService;
import br.com.cezarcirqueira.mirror.app.sync.PeerDownloadClient;
import br.com.cezarcirqueira.mirror.app.sync.SyncConstants;
import br.com.cezarcirqueira.mirror.app.util.HashUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class WebSocketConsumerServiceImpl implements WebSocketConsumerService {

    private static final String FILE_SYNC_QUEUE = "fileSync";
    private static final List<String> SUBSCRIBED_QUEUES = List.of(FILE_SYNC_QUEUE, "mouseCommand");
    private static final long CONNECT_TIMEOUT_SECONDS = 5L;
    private static final long HANDSHAKE_VERIFY_DELAY_MS = 200L;

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicReference<ConnectionMode> currentMode = new AtomicReference<>();
    private final AtomicReference<String> currentServerAddress = new AtomicReference<>();
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    private final WebSocketClient client = new StandardWebSocketClient();
    private final int serverPort;
    private final ObjectMapper objectMapper;
    private final SyncFolderRepository syncFolderRepository;
    private final InstanceIdentityService instanceIdentityService;
    private final PeerDownloadClient peerDownloadClient;

    public WebSocketConsumerServiceImpl(@Value("${server.port:8080}") int serverPort,
                                        ObjectMapper objectMapper,
                                        SyncFolderRepository syncFolderRepository,
                                        InstanceIdentityService instanceIdentityService,
                                        PeerDownloadClient peerDownloadClient) {
        this.serverPort = serverPort;
        this.objectMapper = objectMapper;
        this.syncFolderRepository = syncFolderRepository;
        this.instanceIdentityService = instanceIdentityService;
        this.peerDownloadClient = peerDownloadClient;
    }

    @Override
    public synchronized void connect(ConnectionMode mode, String serverAddress) {
        if (mode == null) {
            throw new IllegalArgumentException("Connection mode is required");
        }
        if (serverAddress == null || serverAddress.isBlank()) {
            throw new IllegalArgumentException("Server address is required");
        }
        refreshConnectedState();
        if (connected.get()) {
            throw new IllegalStateException("Consumer already connected");
        }

        try {
            for (String queue : SUBSCRIBED_QUEUES) {
                URI uri = buildQueueUri(serverAddress, queue);
                WebSocketSession session = client.execute(new ConsumerHandler(queue),
                                new WebSocketHttpHeaders(), uri)
                        .get(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                sessions.put(queue, session);
                log.info("Consumer subscribed to queue '{}' at {}", queue, uri);
            }

            verifySessionsStillOpen();

            currentMode.set(mode);
            currentServerAddress.set(serverAddress);
            connected.set(true);
        } catch (Exception e) {
            closeAllSessions();
            currentMode.set(null);
            currentServerAddress.set(null);
            connected.set(false);
            throw new IllegalStateException("Failed to connect consumer: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void disconnect() {
        if (!connected.get() && sessions.isEmpty()) {
            return;
        }
        closeAllSessions();
        currentMode.set(null);
        currentServerAddress.set(null);
        connected.set(false);
    }

    @Override
    public boolean isConnected() {
        refreshConnectedState();
        return connected.get();
    }

    @Override
    public ConsumerStatusResponse getStatus() {
        refreshConnectedState();
        boolean isConnected = connected.get();
        return ConsumerStatusResponse.builder()
                .connected(isConnected)
                .mode(currentMode.get())
                .serverAddress(currentServerAddress.get())
                .subscribedQueues(isConnected ? List.copyOf(sessions.keySet()) : List.of())
                .build();
    }

    @PreDestroy
    public void destroy() {
        disconnect();
    }

    private synchronized void refreshConnectedState() {
        if (sessions.isEmpty()) {
            if (connected.compareAndSet(true, false)) {
                currentMode.set(null);
                currentServerAddress.set(null);
            }
            return;
        }
        boolean anyOpen = sessions.values().stream().anyMatch(WebSocketSession::isOpen);
        if (!anyOpen) {
            sessions.clear();
            currentMode.set(null);
            currentServerAddress.set(null);
            connected.set(false);
        }
    }

    private synchronized void handleSessionClosed(String queueName, CloseStatus status) {
        log.info("[Consumer] queue='{}' connection closed: {}", queueName, status);
        sessions.remove(queueName);
        if (sessions.isEmpty() || sessions.values().stream().noneMatch(WebSocketSession::isOpen)) {
            sessions.clear();
            currentMode.set(null);
            currentServerAddress.set(null);
            if (connected.compareAndSet(true, false)) {
                log.warn("Consumer auto-disconnected: all subscribed queues are closed");
            }
        }
    }

    private void verifySessionsStillOpen() throws InterruptedException {
        Thread.sleep(HANDSHAKE_VERIFY_DELAY_MS);
        for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
            if (!entry.getValue().isOpen()) {
                throw new IllegalStateException(
                        "Server rejected subscription to queue '" + entry.getKey()
                                + "' (service may not be running)");
            }
        }
    }

    private URI buildQueueUri(String serverAddress, String queueName) {
        String host = serverAddress.contains(":") ? serverAddress : serverAddress + ":" + serverPort;
        return URI.create("ws://" + host + "/ws/" + queueName);
    }

    private String resolvePeerBaseUrl() {
        String addr = currentServerAddress.get();
        if (addr == null || addr.isBlank()) {
            return null;
        }
        int colonIdx = addr.indexOf(':');
        String host = colonIdx >= 0 ? addr.substring(0, colonIdx) : addr;
        return "http://" + host + ":" + serverPort;
    }

    private void processFileSyncEnvelope(String body) {
        WebSocketMessagePayload envelope;
        try {
            envelope = objectMapper.readValue(body, WebSocketMessagePayload.class);
        } catch (IOException ex) {
            log.warn("[Consumer] fileSync envelope parse failed: {}", ex.getMessage());
            return;
        }

        String localInstanceId = instanceIdentityService.getInstanceId();
        if (localInstanceId != null && localInstanceId.equals(envelope.getSenderId())) {
            log.debug("[Consumer] fileSync skipped: senderId matches local instance");
            return;
        }

        if (envelope.getPayload() == null) {
            log.debug("[Consumer] fileSync skipped: empty payload");
            return;
        }

        FileSyncMessage msg;
        try {
            msg = objectMapper.treeToValue(envelope.getPayload(), FileSyncMessage.class);
        } catch (IOException ex) {
            log.warn("[Consumer] fileSync payload parse failed: {}", ex.getMessage());
            return;
        }

        try {
            applyFileSyncMessage(msg);
        } catch (IOException ex) {
            log.warn("[Consumer] fileSync apply failed guid={} path={}: {}",
                    msg.getFolderGuid(), msg.getPath(), ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("[Consumer] fileSync apply error guid={} path={}: {}",
                    msg.getFolderGuid(), msg.getPath(), ex.getMessage(), ex);
        }
    }

    private void applyFileSyncMessage(FileSyncMessage msg) throws IOException {
        if (msg == null || msg.getFolderGuid() == null || msg.getEventType() == null) {
            log.warn("[Consumer] fileSync skipped: incomplete message {}", msg);
            return;
        }

        SyncFolder folder = syncFolderRepository.findByGuid(msg.getFolderGuid()).orElse(null);
        if (folder == null) {
            log.info("[Consumer] fileSync ignored: folder {} not configured locally", msg.getFolderGuid());
            return;
        }

        String relative = msg.getPath() == null ? "" : msg.getPath().replaceFirst("^[/\\\\]+", "");
        if (relative.isBlank()) {
            log.warn("[Consumer] fileSync rejected: empty relative path for folder {}", msg.getFolderGuid());
            return;
        }

        Path base = Paths.get(folder.getBasePath()).toAbsolutePath().normalize();
        Path target = base.resolve(relative).toAbsolutePath().normalize();
        if (!target.startsWith(base)) {
            log.warn("[Consumer] fileSync rejected: path escapes base ({} not under {})", target, base);
            return;
        }

        switch (msg.getEventType()) {
            case DELETED -> applyDelete(msg, target);
            case CREATED, MODIFIED -> applyUpsert(msg, target, relative);
        }
    }

    private void applyDelete(FileSyncMessage msg, Path target) throws IOException {
        boolean removed = Files.deleteIfExists(target);
        log.info("[Consumer] fileSync delete guid={} path={} removed={}",
                msg.getFolderGuid(), target, removed);
    }

    private void applyUpsert(FileSyncMessage msg, Path target, String relative) throws IOException {
        if (Files.isRegularFile(target)) {
            String localHash = HashUtils.sha256Quietly(target);
            if (msg.getHash() != null && msg.getHash().equals(localHash)) {
                log.debug("[Consumer] fileSync same-hash no-op guid={} path={}",
                        msg.getFolderGuid(), target);
                return;
            }
        }

        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path tmp = target.resolveSibling(SyncConstants.SYNC_FILE_PREFIX + target.getFileName());
        String peerBaseUrl = resolvePeerBaseUrl();
        if (peerBaseUrl == null) {
            log.warn("[Consumer] fileSync cannot download: no server address known");
            return;
        }

        log.info("[Consumer] fileSync downloading guid={} path={} from={}",
                msg.getFolderGuid(), relative, peerBaseUrl);
        peerDownloadClient.downloadTo(peerBaseUrl, msg.getFolderGuid(), relative, tmp);

        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }

        log.info("[Consumer] fileSync applied guid={} path={}", msg.getFolderGuid(), target);
    }

    private void closeAllSessions() {
        sessions.forEach((queue, session) -> {
            try {
                if (session.isOpen()) {
                    session.close(CloseStatus.NORMAL);
                }
                log.info("Consumer unsubscribed from queue '{}'", queue);
            } catch (Exception e) {
                log.warn("Error closing consumer session for queue '{}': {}", queue, e.getMessage());
            }
        });
        sessions.clear();
    }

    private class ConsumerHandler extends TextWebSocketHandler {

        private final String queueName;

        ConsumerHandler(String queueName) {
            this.queueName = queueName;
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            String body = message.getPayload();
            log.info("[Consumer] queue='{}' message={}", queueName, body);
            if (FILE_SYNC_QUEUE.equals(queueName)) {
                processFileSyncEnvelope(body);
            }
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            handleSessionClosed(queueName, status);
        }
    }
}
