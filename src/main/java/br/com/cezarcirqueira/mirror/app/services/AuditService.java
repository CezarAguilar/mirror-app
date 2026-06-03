package br.com.cezarcirqueira.mirror.app.services;

import java.nio.file.Path;
import java.util.UUID;

public interface AuditService {

    void downloadSuccess(UUID syncFolderGuid, String clientIp, Path resolvedPath);

    void downloadDenied(UUID syncFolderGuid, String clientIp, String reason);
}
