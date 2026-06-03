package br.com.cezarcirqueira.mirror.app.services;

import br.com.cezarcirqueira.mirror.app.model.dto.FilesystemBrowserResponse;

public interface SystemService {

    FilesystemBrowserResponse listDirectories(String path);
}
