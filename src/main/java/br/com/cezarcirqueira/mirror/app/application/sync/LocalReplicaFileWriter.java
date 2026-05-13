package br.com.cezarcirqueira.mirror.app.application.sync;

import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.LocalReplicaRepository;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity.LocalReplicaEntity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Service;

@Service
public class LocalReplicaFileWriter {

    private final LocalReplicaRepository localReplicaRepository;
    private final FileIndexService fileIndexService;

    public LocalReplicaFileWriter(LocalReplicaRepository localReplicaRepository, FileIndexService fileIndexService) {
        this.localReplicaRepository = localReplicaRepository;
        this.fileIndexService = fileIndexService;
    }

    public void writeRelativeFile(String mirrorGuid, String relativePath, byte[] bytes) throws IOException {
        LocalReplicaEntity replica = localReplicaRepository.findByMirrorGuid(mirrorGuid).orElseThrow();
        Path root = Path.of(replica.getRootPath());
        Path target = root.resolve(relativePath.replace('\\', '/')).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Path escapes replica root");
        }
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
        fileIndexService.upsertFile(mirrorGuid, root, target);
    }

    public void deleteRelativeFile(String mirrorGuid, String relativePath) throws IOException {
        LocalReplicaEntity replica = localReplicaRepository.findByMirrorGuid(mirrorGuid).orElseThrow();
        Path root = Path.of(replica.getRootPath());
        Path target = root.resolve(relativePath.replace('\\', '/')).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Path escapes replica root");
        }
        Files.deleteIfExists(target);
        fileIndexService.removeFile(mirrorGuid, relativePath.replace('\\', '/'));
    }
}
