package br.com.cezarcirqueira.mirror.app.resources;

import br.com.cezarcirqueira.mirror.app.model.dto.ConsumerConnectRequest;
import br.com.cezarcirqueira.mirror.app.model.dto.ConsumerStatusResponse;
import br.com.cezarcirqueira.mirror.app.services.WebSocketConsumerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/websocket-consumer")
@RequiredArgsConstructor
public class WebSocketConsumerResource {

    private final WebSocketConsumerService service;

    @PostMapping("/connect")
    public ResponseEntity<ConsumerStatusResponse> connect(@RequestBody ConsumerConnectRequest request) {
        service.connect(request.getMode(), request.getServerAddress());
        return ResponseEntity.ok(service.getStatus());
    }

    @PostMapping("/disconnect")
    public ResponseEntity<ConsumerStatusResponse> disconnect() {
        service.disconnect();
        return ResponseEntity.ok(service.getStatus());
    }

    @GetMapping("/status")
    public ResponseEntity<ConsumerStatusResponse> getStatus() {
        return ResponseEntity.ok(service.getStatus());
    }
}
