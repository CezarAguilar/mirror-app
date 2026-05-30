package br.com.cezarcirqueira.mirror.app.websocket;

import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
public class WebSocketService {
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final Map<String, WebSocketQueue> activeQueues = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> startTime = new AtomicReference<>(null);

    public boolean start() {
        if (isRunning.compareAndSet(false, true)) {
            startTime.set(Instant.now());
            return true;
        }
        return false;
    }

    public void stop() {
        if (isRunning.compareAndSet(true, false)) {
            startTime.set(null);
            activeQueues.values().forEach(queue -> {
                queue.getConnectedClients().values().forEach(session -> {
                    try {
                        session.close();
                    } catch (IOException e) {
                        // Ignore close errors during shutdown
                    }
                });
            });
            activeQueues.clear();
        }
    }

    public boolean isRunning() {
        return isRunning.get();
    }

    public WebSocketStatus getStatus() {
        return isRunning.get() ? WebSocketStatus.RUNNING : WebSocketStatus.STOPPED;
    }

    public long getUptimeSeconds() {
        Instant start = startTime.get();
        return start != null ? Instant.now().getEpochSecond() - start.getEpochSecond() : 0;
    }

    public WebSocketQueue getQueue(String name) {
        return activeQueues.computeIfAbsent(name, WebSocketQueue::new);
    }

    public Map<String, List<String>> getClients() {
        Map<String, List<String>> clientsByQueue = new HashMap<>();
        activeQueues.forEach((queueName, queue) -> 
            clientsByQueue.put(queueName, new ArrayList<>(queue.getConnectedClients().keySet()))
        );
        return clientsByQueue;
    }
}
