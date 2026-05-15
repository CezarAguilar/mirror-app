package br.com.cezarcirqueira.mirror.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class SlaveWebSocketClient {

    private static final Logger log = LoggerFactory.getLogger(SlaveWebSocketClient.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final br.com.cezarcirqueira.mirror.service.FileSyncService fileSyncService;

    private WebSocket webSocket;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean connecting = new AtomicBoolean(false);
    private final StringBuilder messageBuffer = new StringBuilder();

    public SlaveWebSocketClient(@Lazy br.com.cezarcirqueira.mirror.service.FileSyncService fileSyncService) {
        this.fileSyncService = fileSyncService;
    }

    public void connect(String masterIp, int masterPort) {
        if (connected.get() || !connecting.compareAndSet(false, true)) {
            return;
        }
        URI uri = URI.create("ws://" + masterIp + ":" + masterPort + "/ws/sync");
        log.info("Connecting to master WebSocket at {}", uri);

        HttpClient client = HttpClient.newHttpClient();
        client.newWebSocketBuilder()
                .buildAsync(uri, new Listener())
                .thenAccept(ws -> {
                    this.webSocket = ws;
                    connected.set(true);
                    connecting.set(false);
                    log.info("Connected to master WebSocket");
                })
                .exceptionally(ex -> {
                    connecting.set(false);
                    log.warn("Failed to connect to master WebSocket: {}", ex.getMessage());
                    return null;
                });
    }

    public void ensureConnected(String masterIp, int masterPort) {
        if (!connected.get() && !connecting.get()) {
            log.info("WebSocket not connected, attempting reconnection to {}:{}", masterIp, masterPort);
            connect(masterIp, masterPort);
        }
    }

    public void disconnect() {
        if (webSocket != null && connected.get()) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "closing").join();
            connected.set(false);
        }
    }

    public boolean isConnected() {
        return connected.get();
    }

    public void send(SyncMessage message) {
        if (!connected.get() || webSocket == null) {
            log.warn("Cannot send WS message - not connected to master");
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(message);
            webSocket.sendText(json, true);
        } catch (Exception e) {
            log.error("Error sending WS message to master", e);
        }
    }

    private class Listener implements WebSocket.Listener {

        @Override
        public void onOpen(WebSocket ws) {
            log.info("Slave WebSocket connection opened");
            ws.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            messageBuffer.append(data);
            if (last) {
                String payload = messageBuffer.toString();
                messageBuffer.setLength(0);
                try {
                    SyncMessage message = objectMapper.readValue(payload, SyncMessage.class);
                    log.info("Slave received WS message: action={} guid={}", message.getAction(), message.getGuid());
                    fileSyncService.handleIncomingSlaveMessage(message);
                } catch (Exception e) {
                    log.error("Error processing WS message from master", e);
                }
            }
            ws.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onPing(WebSocket ws, ByteBuffer message) {
            ws.sendPong(message);
            ws.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            log.warn("Slave WebSocket error: {}", error.getMessage());
            connected.set(false);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            log.info("Slave WebSocket closed: {}", reason);
            connected.set(false);
            return CompletableFuture.completedFuture(null);
        }
    }
}
