package br.com.cezarcirqueira.mirror.app.sync;

import br.com.cezarcirqueira.mirror.app.model.dto.GenericMessageApi;
import br.com.cezarcirqueira.mirror.app.model.dto.PublishMessageResponse;
import br.com.cezarcirqueira.mirror.app.services.WebSocketConsumerService;
import br.com.cezarcirqueira.mirror.app.services.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboundDispatcher {

    private final WebSocketService webSocketService;
    private final WebSocketConsumerService webSocketConsumerService;

    public boolean publish(String queueName, String destinationId, GenericMessageApi payload) {
        if (webSocketService.isRunning()) {
            try {
                PublishMessageResponse response = webSocketService.publish(queueName, destinationId, payload);
                log.debug("Published via local server queue='{}' messageId={} targets={}",
                        queueName, response.getMessageId(),
                        response.getTargets() == null ? 0 : response.getTargets().size());
                return true;
            } catch (RuntimeException ex) {
                log.warn("Local server publish failed for queue '{}': {}", queueName, ex.getMessage());
                return false;
            }
        }

        if (webSocketConsumerService.isConnected()) {
            boolean sent = webSocketConsumerService.publish(queueName, destinationId, payload);
            if (!sent) {
                log.warn("Consumer publish failed for queue '{}'", queueName);
            }
            return sent;
        }

        log.debug("No transport available to publish on queue '{}'", queueName);
        return false;
    }
}
