package br.com.cezarcirqueira.mirror.app.services.impl;

import br.com.cezarcirqueira.mirror.app.services.AuditService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.UUID;

@Slf4j
@Service
public class AuditServiceImpl implements AuditService {

    private static final Marker AUDIT = MarkerFactory.getMarker("AUDIT");

    @Override
    public void downloadSuccess(UUID syncFolderGuid, String clientIp, Path resolvedPath) {
        log.info(AUDIT, "download.success guid={} clientIp={} path={}",
                syncFolderGuid, safe(clientIp), resolvedPath);
    }

    @Override
    public void downloadDenied(UUID syncFolderGuid, String clientIp, String reason) {
        log.warn(AUDIT, "download.denied guid={} clientIp={} reason={}",
                syncFolderGuid, safe(clientIp), reason);
    }

    private String safe(String value) {
        return value == null ? "unknown" : value;
    }
}
