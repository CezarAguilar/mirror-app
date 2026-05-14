package br.com.cezarcirqueira.mirror.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class MasterWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(MasterWebSocketHandler.class);

    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final br.com.cezarcirqueira.mirror.service.FileSyncService fileSyncService;

    public MasterWebSocketHandler(@Lazy br.com.cezarcirqueira.mirror.service.FileSyncService fileSyncService) {
        this.fileSyncService = fileSyncService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("WebSocket client connected: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("WebSocket client disconnected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        SyncMessage syncMessage = objectMapper.readValue(message.getPayload(), SyncMessage.class);
        fileSyncService.handleIncomingMasterMessage(syncMessage, session);
    }

    public void broadcast(SyncMessage message, WebSocketSession exclude) {
        try {
            String json = objectMapper.writeValueAsString(message);
            for (WebSocketSession s : sessions) {
                if (s.isOpen() && (exclude == null || !s.getId().equals(exclude.getId()))) {
                    try { s.sendMessage(new TextMessage(json)); }
                    catch (IOException e) { log.warn("Failed to send to session {}", s.getId()); }
                }
            }
        } catch (Exception e) { log.error("Error broadcasting WS message", e); }
    }

    public void broadcast(SyncMessage message) { broadcast(message, null); }
}
