package br.com.cezarcirqueira.mirror.config;

import br.com.cezarcirqueira.mirror.websocket.MasterWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final MasterWebSocketHandler masterWebSocketHandler;

    public WebSocketConfig(MasterWebSocketHandler masterWebSocketHandler) {
        this.masterWebSocketHandler = masterWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(masterWebSocketHandler, "/ws/sync")
                .setAllowedOrigins("*");
    }
}
