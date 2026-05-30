package br.com.cezarcirqueira.mirror.app.resources;

import br.com.cezarcirqueira.mirror.app.services.WebSocketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/websocket")
@RequiredArgsConstructor
public class WebSocketResource {

    private final WebSocketService service;

    @PostMapping("/start")
    public ResponseEntity<Void> start() {
        return service.start() ? ResponseEntity.ok().build() : ResponseEntity.badRequest().build();
    }

    @PostMapping("/stop")
    public ResponseEntity<Void> stop() {
        service.stop();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "status", service.getStatus(),
                "uptime_seconds", service.getUptimeSeconds()
        ));
    }

    @GetMapping("/clients")
    public ResponseEntity<Map<String, Object>> getClients() {
        if (!service.isRunning()) {
            return ResponseEntity.ok(Collections.singletonMap("error", "Service is not running"));
        }
        return ResponseEntity.ok(Map.of("queues", service.getClients()));
    }
}
