package br.com.cezarcirqueira.mirror.app.websocket;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/websocket")
public class WebSocketController {

    private final WebSocketService webSocketService;

    public WebSocketController(WebSocketService webSocketService) {
        this.webSocketService = webSocketService;
    }

    @PostMapping("/start")
    public ResponseEntity<Void> start() {
        return webSocketService.start() ? ResponseEntity.ok().build() : ResponseEntity.badRequest().build();
    }

    @PostMapping("/stop")
    public ResponseEntity<Void> stop() {
        webSocketService.stop();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "status", webSocketService.getStatus(),
                "uptime_seconds", webSocketService.getUptimeSeconds()
        ));
    }

    @GetMapping("/clients")
    public ResponseEntity<Map<String, Object>> getClients() {
        if (!webSocketService.isRunning()) {
            return ResponseEntity.ok(Collections.singletonMap("error", "Service is not running"));
        }
        return ResponseEntity.ok(Map.of("queues", webSocketService.getClients()));
    }
}
