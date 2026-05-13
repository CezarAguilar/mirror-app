package br.com.cezarcirqueira.mirror.app.application.sync;

import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.LocalReplicaRepository;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.MirrorDefinitionRepository;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity.LocalReplicaEntity;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity.MirrorDefinitionEntity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InboundMirrorService {

    private final MirrorDefinitionRepository mirrorDefinitionRepository;
    private final LocalReplicaRepository localReplicaRepository;
    private final FileIndexService fileIndexService;
    private final RecentRemoteWriteRegistry recentRemoteWriteRegistry;

    public InboundMirrorService(
            MirrorDefinitionRepository mirrorDefinitionRepository,
            LocalReplicaRepository localReplicaRepository,
            FileIndexService fileIndexService,
            RecentRemoteWriteRegistry recentRemoteWriteRegistry) {
        this.mirrorDefinitionRepository = mirrorDefinitionRepository;
        this.localReplicaRepository = localReplicaRepository;
        this.fileIndexService = fileIndexService;
        this.recentRemoteWriteRegistry = recentRemoteWriteRegistry;
    }

    @Transactional
    public void writeRemoteFile(String mirrorGuid, String relativePath, byte[] body) throws IOException {
        MirrorDefinitionEntity mirror = mirrorDefinitionRepository.findById(mirrorGuid).orElseThrow();
        if (mirror.isMirrorPaused()) {
            throw new IllegalStateException("Mirror is paused");
        }
        LocalReplicaEntity replica = localReplicaRepository.findByMirrorGuid(mirrorGuid).orElseThrow();
        if (replica.isReplicaPaused()) {
            throw new IllegalStateException("Replica is paused");
        }
        Path root = Path.of(replica.getRootPath());
        Path target = root.resolve(relativePath.replace('\\', '/')).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Path escapes replica root");
        }
        Files.createDirectories(target.getParent());
        Files.write(target, body);
        recentRemoteWriteRegistry.markRemoteWrite(mirrorGuid, relativePath.replace('\\', '/'));
        fileIndexService.upsertFile(mirrorGuid, root, target);
    }

    @Transactional
    public void deleteRemoteFile(String mirrorGuid, String relativePath) throws IOException {
        MirrorDefinitionEntity mirror = mirrorDefinitionRepository.findById(mirrorGuid).orElseThrow();
        if (mirror.isMirrorPaused()) {
            throw new IllegalStateException("Mirror is paused");
        }
        LocalReplicaEntity replica = localReplicaRepository.findByMirrorGuid(mirrorGuid).orElseThrow();
        if (replica.isReplicaPaused()) {
            throw new IllegalStateException("Replica is paused");
        }
        Path root = Path.of(replica.getRootPath());
        Path target = root.resolve(relativePath.replace('\\', '/')).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Path escapes replica root");
        }
        Files.deleteIfExists(target);
        recentRemoteWriteRegistry.markRemoteWrite(mirrorGuid, relativePath.replace('\\', '/'));
        fileIndexService.removeFile(mirrorGuid, relativePath.replace('\\', '/'));
    }
}
