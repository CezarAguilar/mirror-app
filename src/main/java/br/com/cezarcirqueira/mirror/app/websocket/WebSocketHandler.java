package br.com.cezarcirqueira.mirror.app.websocket;

import br.com.cezarcirqueira.mirror.app.model.WebSocketQueue;
import br.com.cezarcirqueira.mirror.app.model.dto.WebSocketMessagePayload;
import br.com.cezarcirqueira.mirror.app.services.WebSocketService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class WebSocketHandler extends TextWebSocketHandler {

    private final WebSocketService webSocketService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (!webSocketService.isRunning()) {
            session.close(CloseStatus.SERVER_ERROR.withReason("Service is not running"));
            return;
        }
        String queueName = getQueueName(session);
        String clientId = getClientId(session);
        webSocketService.getQueue(queueName).addClient(clientId, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String queueName = getQueueName(session);
        String clientId = getClientId(session);
        WebSocketQueue queue = webSocketService.getQueue(queueName);
        WebSocketMessagePayload payload = objectMapper.readValue(message.getPayload(), WebSocketMessagePayload.class);

        if ("ACK".equals(payload.getType())) {
            handleAck(queue, clientId, payload.getMessageId());
        } else {
            handleDataMessage(queue, clientId, payload);
        }
    }

    private void handleDataMessage(WebSocketQueue queue, String senderId, WebSocketMessagePayload payload) throws IOException {
        String messageId = UUID.randomUUID().toString();
        payload.setMessageId(messageId);
        payload.setQueue(queue.getName());

        Map<String, WebSocketSession> targets;
        if (payload.getDestinationId() != null) {
            targets = Map.of(payload.getDestinationId(), queue.getConnectedClients().get(payload.getDestinationId()));
        } else {
            targets = queue.getConnectedClients().entrySet().stream()
                    .filter(entry -> !entry.getKey().equals(senderId))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }

        if (!targets.isEmpty()) {
            queue.getPendingAcks().put(messageId, targets.keySet().stream()
                    .collect(Collectors.toMap(id -> id, id -> false)));

            WebSocketMessagePayload newPayload = WebSocketMessagePayload.builder()
                    .type("NEW_MESSAGE")
                    .messageId(messageId)
                    .queue(queue.getName())
                    .senderId(payload.getSenderId())
                    .payload(payload.getPayload())
                    .build();

            TextMessage textMessage = new TextMessage(objectMapper.writeValueAsString(newPayload));
            for (WebSocketSession targetSession : targets.values()) {
                if (targetSession != null && targetSession.isOpen()) {
                    targetSession.sendMessage(textMessage);
                }
            }
        }
    }

    private void handleAck(WebSocketQueue queue, String clientId, String messageId) {
        Map<String, Boolean> acks = queue.getPendingAcks().get(messageId);
        if (acks != null) {
            acks.put(clientId, true);
            if (acks.values().stream().allMatch(Boolean::booleanValue)) {
                queue.getPendingAcks().remove(messageId);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String queueName = getQueueName(session);
        String clientId = getClientId(session);
        webSocketService.getQueue(queueName).removeClient(clientId);
    }

    private String getQueueName(WebSocketSession session) {
        String path = session.getUri().getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private String getClientId(WebSocketSession session) {
        // In a real app, you'd get this from the handshake or a token
        return session.getRemoteAddress().toString();
    }
}
