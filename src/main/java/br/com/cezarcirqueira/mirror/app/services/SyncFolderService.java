package br.com.cezarcirqueira.mirror.app.services;

import br.com.cezarcirqueira.mirror.app.model.dto.SyncFolderRequest;
import br.com.cezarcirqueira.mirror.app.model.dto.SyncFolderResponse;
import java.util.List;
import java.util.UUID;

public interface SyncFolderService {

    SyncFolderResponse create(SyncFolderRequest request);

    SyncFolderResponse findByGuid(UUID guid);

    List<SyncFolderResponse> findAll();

    SyncFolderResponse update(UUID guid, SyncFolderRequest request);

    void delete(UUID guid);
}
