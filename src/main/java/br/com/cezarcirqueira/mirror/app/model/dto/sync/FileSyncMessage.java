package br.com.cezarcirqueira.mirror.app.model.dto.sync;

import br.com.cezarcirqueira.mirror.app.model.dto.GenericMessageApi;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class FileSyncMessage extends GenericMessageApi {
    private UUID folderGuid;
    private String path;
    private String hash;
    private FileSyncEventType eventType;
}
