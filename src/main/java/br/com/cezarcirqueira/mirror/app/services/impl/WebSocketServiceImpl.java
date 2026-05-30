package br.com.cezarcirqueira.mirror.app.services.impl;

import br.com.cezarcirqueira.mirror.app.model.WebSocketQueue;
import br.com.cezarcirqueira.mirror.app.model.WebSocketStatus;
import br.com.cezarcirqueira.mirror.app.services.WebSocketService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class WebSocketServiceImpl implements WebSocketService {

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
}
