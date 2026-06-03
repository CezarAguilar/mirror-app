package br.com.cezarcirqueira.mirror.app.config;

import br.com.cezarcirqueira.mirror.app.websocket.ServiceRunningHandshakeInterceptor;
import br.com.cezarcirqueira.mirror.app.websocket.WebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final WebSocketHandler webSocketHandler;
    private final ServiceRunningHandshakeInterceptor serviceRunningHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketHandler, "/ws/{queueName}")
                .addInterceptors(serviceRunningHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
