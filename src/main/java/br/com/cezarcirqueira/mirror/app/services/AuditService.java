package br.com.cezarcirqueira.mirror.app.services;

import java.nio.file.Path;
import java.util.UUID;

public interface AuditService {

    /**
     * Records a successful download. The cleartext relative path is intentionally
     * never logged: only the resolved (absolute) path that was actually served.
     */
    void downloadSuccess(UUID syncFolderGuid, String clientIp, Path resolvedPath);

    /**
     * Records a denied download attempt with the reason already free of sensitive
     * data (e.g. "path traversal", "replay", "file not found").
     */
    void downloadDenied(UUID syncFolderGuid, String clientIp, String reason);
}
