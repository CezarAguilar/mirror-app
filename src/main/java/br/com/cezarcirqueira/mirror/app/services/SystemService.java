package br.com.cezarcirqueira.mirror.app.services;

import br.com.cezarcirqueira.mirror.app.model.dto.FilesystemBrowserResponse;

public interface SystemService {

    /**
     * Lists the directories visible at {@code path}. When {@code path} is {@code null}
     * or blank, returns the filesystem roots (drives on Windows, {@code /} on Unix).
     */
    FilesystemBrowserResponse listDirectories(String path);
}
