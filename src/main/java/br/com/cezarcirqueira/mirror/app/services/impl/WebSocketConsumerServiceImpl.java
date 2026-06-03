package br.com.cezarcirqueira.mirror.app.services.impl;

import br.com.cezarcirqueira.mirror.app.model.ConnectionMode;
import br.com.cezarcirqueira.mirror.app.model.dto.ConsumerStatusResponse;
import br.com.cezarcirqueira.mirror.app.services.WebSocketConsumerService;
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

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class WebSocketConsumerServiceImpl implements WebSocketConsumerService {

    private static final List<String> SUBSCRIBED_QUEUES = List.of("fileSync", "mouseCommand");
    private static final long CONNECT_TIMEOUT_SECONDS = 5L;
    private static final long HANDSHAKE_VERIFY_DELAY_MS = 200L;

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicReference<ConnectionMode> currentMode = new AtomicReference<>();
    private final AtomicReference<String> currentServerAddress = new AtomicReference<>();
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    private final WebSocketClient client = new StandardWebSocketClient();
    private final int serverPort;

    public WebSocketConsumerServiceImpl(@Value("${server.port:8080}") int serverPort) {
        this.serverPort = serverPort;
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
            log.info("[Consumer] queue='{}' message={}", queueName, message.getPayload());
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            handleSessionClosed(queueName, status);
        }
    }
}
