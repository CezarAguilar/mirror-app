package br.com.cezarcirqueira.mirror.app.model.dto;

import br.com.cezarcirqueira.mirror.app.model.dto.sync.FileSyncMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishMessageRequest {
    private String destinationId;
    private FileSyncMessage payload;
}
