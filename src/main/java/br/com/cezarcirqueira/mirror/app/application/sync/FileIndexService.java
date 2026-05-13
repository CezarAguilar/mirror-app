package br.com.cezarcirqueira.mirror.app.application.sync;

import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.FileIndexEntryRepository;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.LocalReplicaRepository;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity.FileIndexEntryEntity;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity.FileIndexEntryId;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity.LocalReplicaEntity;
import br.com.cezarcirqueira.mirror.app.application.filesystem.Sha256Hasher;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FileIndexService {

    private static final Logger log = LoggerFactory.getLogger(FileIndexService.class);

    private final LocalReplicaRepository localReplicaRepository;
    private final FileIndexEntryRepository fileIndexEntryRepository;
    private final Sha256Hasher sha256Hasher;

    public FileIndexService(
            LocalReplicaRepository localReplicaRepository,
            FileIndexEntryRepository fileIndexEntryRepository,
            Sha256Hasher sha256Hasher) {
        this.localReplicaRepository = localReplicaRepository;
        this.fileIndexEntryRepository = fileIndexEntryRepository;
        this.sha256Hasher = sha256Hasher;
    }

    @Transactional
    public void removeIndexForMirror(String mirrorGuid) {
        fileIndexEntryRepository.deleteAllByMirrorGuid(mirrorGuid);
    }

    @Transactional
    public void upsertFile(String mirrorGuid, Path root, Path absoluteFile) throws IOException {
        Path relative = root.relativize(absoluteFile).normalize();
        String rel = relative.toString().replace('\\', '/');
        if (rel.isEmpty() || rel.endsWith("/")) {
            return;
        }
        String hash = sha256Hasher.hashFile(absoluteFile);
        long size = Files.size(absoluteFile);
        long mtime = Files.getLastModifiedTime(absoluteFile).toMillis();
        FileIndexEntryId id = new FileIndexEntryId(mirrorGuid, rel);
        FileIndexEntryEntity entity =
                fileIndexEntryRepository.findById(id).orElse(new FileIndexEntryEntity(id, hash, size, mtime));
        entity.setSha256Hex(hash);
        entity.setSizeBytes(size);
        entity.setLastModifiedEpochMs(mtime);
        fileIndexEntryRepository.save(entity);
    }

    @Transactional
    public void removeFile(String mirrorGuid, String relativePath) {
        fileIndexEntryRepository.deleteById(new FileIndexEntryId(mirrorGuid, relativePath.replace('\\', '/')));
    }

    @Transactional(readOnly = true)
    public List<FileIndexEntryEntity> listIndex(String mirrorGuid) {
        return fileIndexEntryRepository.findByIdMirrorGuid(mirrorGuid);
    }

    @Transactional
    public RescanDelta fullRescanMirrorReturningDelta(String mirrorGuid) throws IOException {
        Optional<LocalReplicaEntity> replica = localReplicaRepository.findByMirrorGuid(mirrorGuid);
        if (replica.isEmpty()) {
            return RescanDelta.empty();
        }
        Map<String, String> previousHashes = RescanDelta.toHashMap(fileIndexEntryRepository.findByIdMirrorGuid(mirrorGuid));

        Path root = Path.of(replica.get().getRootPath());
        if (!Files.isDirectory(root)) {
            log.warn("Replica root is not a directory: {}", root);
            return RescanDelta.empty();
        }
        List<String> seen = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .forEach(
                            p -> {
                                try {
                                    Path relative = root.relativize(p).normalize();
                                    String rel = relative.toString().replace('\\', '/');
                                    if (rel.isEmpty()) {
                                        return;
                                    }
                                    String hash = sha256Hasher.hashFile(p);
                                    long size = Files.size(p);
                                    long mtime = Files.getLastModifiedTime(p).toMillis();
                                    FileIndexEntryId id = new FileIndexEntryId(mirrorGuid, rel);
                                    FileIndexEntryEntity entity = fileIndexEntryRepository
                                            .findById(id)
                                            .orElse(new FileIndexEntryEntity(id, hash, size, mtime));
                                    entity.setSha256Hex(hash);
                                    entity.setSizeBytes(size);
                                    entity.setLastModifiedEpochMs(mtime);
                                    fileIndexEntryRepository.save(entity);
                                    seen.add(rel);
                                } catch (IOException e) {
                                    log.warn("Failed to index {}", p, e);
                                }
                            });
        }
        List<FileIndexEntryEntity> existing = fileIndexEntryRepository.findByIdMirrorGuid(mirrorGuid);
        for (FileIndexEntryEntity row : existing) {
            if (!seen.contains(row.getId().getRelativePath())) {
                fileIndexEntryRepository.deleteById(row.getId());
            }
        }

        Map<String, String> newHashes = RescanDelta.toHashMap(fileIndexEntryRepository.findByIdMirrorGuid(mirrorGuid));
        return RescanDelta.compute(previousHashes, newHashes);
    }

    @Transactional
    public void fullRescanMirror(String mirrorGuid) throws IOException {
        fullRescanMirrorReturningDelta(mirrorGuid);
    }
}
