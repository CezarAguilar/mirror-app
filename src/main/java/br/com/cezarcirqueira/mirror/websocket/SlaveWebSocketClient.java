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
    private final StringBuilder messageBuffer = new StringBuilder();

    public SlaveWebSocketClient(@Lazy br.com.cezarcirqueira.mirror.service.FileSyncService fileSyncService) {
        this.fileSyncService = fileSyncService;
    }

    public void connect(String masterIp, int masterPort) {
        if (connected.get()) return;
        URI uri = URI.create("ws://" + masterIp + ":" + masterPort + "/ws/sync");
        HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(uri, new Listener())
                .thenAccept(ws -> { this.webSocket = ws; connected.set(true); })
                .exceptionally(ex -> { log.warn("WS connect failed: {}", ex.getMessage()); return null; });
    }

    public void disconnect() {
        if (webSocket != null && connected.get()) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "closing").join();
            connected.set(false);
        }
    }

    public boolean isConnected() { return connected.get(); }

    public void send(SyncMessage message) {
        if (!connected.get() || webSocket == null) return;
        try { webSocket.sendText(objectMapper.writeValueAsString(message), true); }
        catch (Exception e) { log.error("Error sending WS message", e); }
    }

    private class Listener implements WebSocket.Listener {
        @Override public void onOpen(WebSocket ws) { ws.request(1); }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            messageBuffer.append(data);
            if (last) {
                String payload = messageBuffer.toString();
                messageBuffer.setLength(0);
                try {
                    SyncMessage msg = objectMapper.readValue(payload, SyncMessage.class);
                    fileSyncService.handleIncomingSlaveMessage(msg);
                } catch (Exception e) { log.error("Error processing WS message", e); }
            }
            ws.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onPing(WebSocket ws, ByteBuffer msg) {
            ws.sendPong(msg); ws.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override public void onError(WebSocket ws, Throwable e) { connected.set(false); }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
            connected.set(false);
            return CompletableFuture.completedFuture(null);
        }
    }
}
