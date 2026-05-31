package br.com.cezarcirqueira.mirror.app.services;

import br.com.cezarcirqueira.mirror.app.model.ConnectionMode;
import br.com.cezarcirqueira.mirror.app.model.dto.ConsumerStatusResponse;

public interface WebSocketConsumerService {

    void connect(ConnectionMode mode, String serverAddress);

    void disconnect();

    boolean isConnected();

    ConsumerStatusResponse getStatus();
}
