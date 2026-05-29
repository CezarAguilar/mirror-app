package br.com.cezarcirqueira.mirror.app.services;

import br.com.cezarcirqueira.mirror.app.model.SyncFolder;

import java.util.UUID;

public interface FolderWatcherService {

    void registerFolder(SyncFolder folder);

    void unregisterFolder(UUID guid);
}
