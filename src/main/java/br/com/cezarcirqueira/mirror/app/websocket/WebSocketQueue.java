package br.com.cezarcirqueira.mirror.app.websocket;

import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WebSocketQueue {
    private final String name;
    private final Map<String, WebSocketSession> connectedClients = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Boolean>> pendingAcks = new ConcurrentHashMap<>();

    public WebSocketQueue(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Map<String, WebSocketSession> getConnectedClients() {
        return connectedClients;
    }

    public Map<String, Map<String, Boolean>> getPendingAcks() {
        return pendingAcks;
    }

    public void addClient(String clientId, WebSocketSession session) {
        connectedClients.put(clientId, session);
    }

    public void removeClient(String clientId) {
        connectedClients.remove(clientId);
        pendingAcks.values().forEach(acks -> acks.remove(clientId));
        pendingAcks.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }
}
