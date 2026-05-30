package br.com.cezarcirqueira.mirror.app.services;

import br.com.cezarcirqueira.mirror.app.model.WebSocketQueue;
import br.com.cezarcirqueira.mirror.app.model.WebSocketStatus;

import java.util.List;
import java.util.Map;

public interface WebSocketService {

    boolean start();

    void stop();

    boolean isRunning();

    WebSocketStatus getStatus();

    long getUptimeSeconds();

    WebSocketQueue getQueue(String name);

    Map<String, List<String>> getClients();
}
