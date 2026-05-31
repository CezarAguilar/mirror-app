package br.com.cezarcirqueira.mirror.app.model.dto;

import br.com.cezarcirqueira.mirror.app.model.ConnectionMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumerStatusResponse {
    private boolean connected;
    private ConnectionMode mode;
    private String serverAddress;
    private List<String> subscribedQueues;
}
