package br.com.cezarcirqueira.mirror.app.services.impl;

import br.com.cezarcirqueira.mirror.app.model.WebSocketQueue;
import br.com.cezarcirqueira.mirror.app.model.WebSocketStatus;
import br.com.cezarcirqueira.mirror.app.model.dto.GenericMessageApi;
import br.com.cezarcirqueira.mirror.app.model.dto.PublishMessageResponse;
import br.com.cezarcirqueira.mirror.app.model.dto.WebSocketMessagePayload;
import br.com.cezarcirqueira.mirror.app.services.WebSocketService;
import br.com.cezarcirqueira.mirror.app.sync.InstanceIdentityService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketServiceImpl implements WebSocketService {

    private final ObjectMapper objectMapper;
    private final InstanceIdentityService instanceIdentityService;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final Map<String, WebSocketQueue> activeQueues = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> startTime = new AtomicReference<>(null);

    @Override
    public boolean start() {
        if (isRunning.compareAndSet(false, true)) {
            startTime.set(Instant.now());
            return true;
        }
        return false;
    }

    @Override
    public void stop() {
        if (isRunning.compareAndSet(true, false)) {
            startTime.set(null);
            activeQueues.values().forEach(queue -> queue.getConnectedClients().values().forEach(session -> {
                try {
                    session.close();
                } catch (IOException e) {
                    // Ignore close errors during shutdown
                }
            }));
            activeQueues.clear();
        }
    }

    @Override
    public boolean isRunning() {
        return isRunning.get();
    }

    @Override
    public WebSocketStatus getStatus() {
        return isRunning.get() ? WebSocketStatus.RUNNING : WebSocketStatus.STOPPED;
    }

    @Override
    public long getUptimeSeconds() {
        Instant start = startTime.get();
        return start != null ? Instant.now().getEpochSecond() - start.getEpochSecond() : 0;
    }

    @Override
    public WebSocketQueue getQueue(String name) {
        return activeQueues.computeIfAbsent(name, WebSocketQueue::new);
    }

    @Override
    public Map<String, List<String>> getClients() {
        Map<String, List<String>> clientsByQueue = new HashMap<>();
        activeQueues.forEach((queueName, queue) ->
                clientsByQueue.put(queueName, new ArrayList<>(queue.getConnectedClients().keySet()))
        );
        return clientsByQueue;
    }

    @Override
    public PublishMessageResponse publish(String queueName, String destinationId, GenericMessageApi payload) {
        if (queueName == null || queueName.isBlank()) {
            throw new IllegalArgumentException("Queue name is required");
        }
        if (!isRunning.get()) {
            throw new IllegalStateException("WebSocket service is not running");
        }

        WebSocketQueue queue = getQueue(queueName);
        String messageId = UUID.randomUUID().toString();

        Map<String, WebSocketSession> targets = resolveTargets(queue, destinationId);
        List<String> deliveredTo = new ArrayList<>();

        if (targets.isEmpty()) {
            return PublishMessageResponse.builder()
                    .messageId(messageId)
                    .queue(queueName)
                    .targets(deliveredTo)
                    .build();
        }

        JsonNode payloadNode = payload == null ? null : objectMapper.valueToTree(payload);
        WebSocketMessagePayload outboundPayload = WebSocketMessagePayload.builder()
                .type("NEW_MESSAGE")
                .messageId(messageId)
                .queue(queueName)
                .senderId(instanceIdentityService.getInstanceId())
                .payload(payloadNode)
                .build();

        TextMessage textMessage;
        try {
            textMessage = new TextMessage(objectMapper.writeValueAsString(outboundPayload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize message payload", e);
        }

        Map<String, Boolean> pendingAcks = new LinkedHashMap<>();
        targets.forEach((clientId, session) -> {
            if (session == null || !session.isOpen()) {
                return;
            }
            try {
                session.sendMessage(textMessage);
                pendingAcks.put(clientId, false);
                deliveredTo.add(clientId);
            } catch (IOException e) {
                log.warn("Failed to deliver message {} to client {}: {}", messageId, clientId, e.getMessage());
            }
        });

        if (!pendingAcks.isEmpty()) {
            queue.getPendingAcks().put(messageId, pendingAcks);
        }

        return PublishMessageResponse.builder()
                .messageId(messageId)
                .queue(queueName)
                .targets(deliveredTo)
                .build();
    }

    private Map<String, WebSocketSession> resolveTargets(WebSocketQueue queue, String destinationId) {
        Map<String, WebSocketSession> connected = queue.getConnectedClients();
        if (destinationId == null || destinationId.isBlank()) {
            return new LinkedHashMap<>(connected);
        }
        WebSocketSession target = connected.get(destinationId);
        if (target == null) {
            throw new IllegalArgumentException(
                    "Destination '" + destinationId + "' is not connected to queue '" + queue.getName() + "'");
        }
        return Map.of(destinationId, target);
    }
}
