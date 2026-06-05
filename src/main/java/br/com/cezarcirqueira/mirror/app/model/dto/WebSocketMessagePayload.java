package br.com.cezarcirqueira.mirror.app.model.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketMessagePayload {
    private String type;
    private String messageId;
    private String queue;
    private String senderId;
    private String destinationId;
    private JsonNode payload;
}
