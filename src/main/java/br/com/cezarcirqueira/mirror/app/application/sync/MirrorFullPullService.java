package br.com.cezarcirqueira.mirror.app.application.sync;

import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.LocalReplicaRepository;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.MirrorDefinitionRepository;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.PeerEndpointRepository;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity.LocalReplicaEntity;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity.MirrorDefinitionEntity;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity.PeerEndpointEntity;
import br.com.cezarcirqueira.mirror.app.adapter.out.peer.PeerMirrorHttpClient;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MirrorFullPullService {

    private static final Logger log = LoggerFactory.getLogger(MirrorFullPullService.class);

    private final MirrorDefinitionRepository mirrorDefinitionRepository;
    private final LocalReplicaRepository localReplicaRepository;
    private final PeerEndpointRepository peerEndpointRepository;
    private final PeerMirrorHttpClient peerMirrorHttpClient;
    private final LocalReplicaFileWriter localReplicaFileWriter;
    private final FileIndexService fileIndexService;

    public MirrorFullPullService(
            MirrorDefinitionRepository mirrorDefinitionRepository,
            LocalReplicaRepository localReplicaRepository,
            PeerEndpointRepository peerEndpointRepository,
            PeerMirrorHttpClient peerMirrorHttpClient,
            LocalReplicaFileWriter localReplicaFileWriter,
            FileIndexService fileIndexService) {
        this.mirrorDefinitionRepository = mirrorDefinitionRepository;
        this.localReplicaRepository = localReplicaRepository;
        this.peerEndpointRepository = peerEndpointRepository;
        this.peerMirrorHttpClient = peerMirrorHttpClient;
        this.localReplicaFileWriter = localReplicaFileWriter;
        this.fileIndexService = fileIndexService;
    }

    public void pullRemoteSnapshotAuthoritative(String mirrorGuid) throws IOException, InterruptedException {
        MirrorDefinitionEntity mirror = mirrorDefinitionRepository.findById(mirrorGuid).orElseThrow();
        LocalReplicaEntity replica = localReplicaRepository.findByMirrorGuid(mirrorGuid).orElseThrow();
        PeerEndpointEntity peer =
                peerEndpointRepository.findAll().stream().findFirst().orElseThrow();
        Path root = Path.of(replica.getRootPath());
        List<PeerMirrorHttpClient.ManifestEntryResponse> remote =
                peerMirrorHttpClient.fetchManifest(peer.getBaseUrl(), mirrorGuid, mirror.getSharedSecret());
        for (PeerMirrorHttpClient.ManifestEntryResponse row : remote) {
            byte[] bytes = peerMirrorHttpClient.downloadFile(
                    peer.getBaseUrl(), mirrorGuid, mirror.getSharedSecret(), row.relativePath());
            localReplicaFileWriter.writeRelativeFile(mirrorGuid, row.relativePath(), bytes);
        }
        List<String> locals;
        try (var walk = java.nio.file.Files.walk(root)) {
            locals = walk.filter(java.nio.file.Files::isRegularFile)
                    .map(p -> root.relativize(p).toString().replace('\\', '/'))
                    .toList();
        }
        Set<String> remoteSet =
                remote.stream().map(PeerMirrorHttpClient.ManifestEntryResponse::relativePath).collect(Collectors.toSet());
        for (String rel : locals) {
            if (!remoteSet.contains(rel)) {
                localReplicaFileWriter.deleteRelativeFile(mirrorGuid, rel);
            }
        }
        fileIndexService.fullRescanMirrorReturningDelta(mirrorGuid);
    }
}
