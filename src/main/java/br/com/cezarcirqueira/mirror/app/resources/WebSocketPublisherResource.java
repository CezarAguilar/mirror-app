package br.com.cezarcirqueira.mirror.app.resources;

import br.com.cezarcirqueira.mirror.app.model.dto.PublishMessageRequest;
import br.com.cezarcirqueira.mirror.app.model.dto.PublishMessageResponse;
import br.com.cezarcirqueira.mirror.app.services.WebSocketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TEMPORARY: Test-only endpoint used to publish arbitrary payloads to a WebSocket queue.
 * Will be removed once the production producers are in place.
 */
@RestController
@RequestMapping("/api/websocket-publisher")
@RequiredArgsConstructor
public class WebSocketPublisherResource {

    private final WebSocketService service;

    @PostMapping("/{queueName}")
    public ResponseEntity<PublishMessageResponse> publish(@PathVariable String queueName,
                                                          @RequestBody PublishMessageRequest request) {
        PublishMessageResponse response = service.publish(queueName, request.getDestinationId(), request.getPayload());
        return ResponseEntity.ok(response);
    }
}
