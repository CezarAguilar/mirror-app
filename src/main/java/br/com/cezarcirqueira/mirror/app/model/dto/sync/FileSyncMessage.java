package br.com.cezarcirqueira.mirror.app.model.dto.sync;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileSyncMessage {
    private UUID folderGuid;
    private String path;
    private String hash;
    private FileSyncEventType eventType;
}
