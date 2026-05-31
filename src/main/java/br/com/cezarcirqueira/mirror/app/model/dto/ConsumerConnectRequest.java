package br.com.cezarcirqueira.mirror.app.model.dto;

import br.com.cezarcirqueira.mirror.app.model.ConnectionMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumerConnectRequest {
    private ConnectionMode mode;
    private String serverAddress;
}
