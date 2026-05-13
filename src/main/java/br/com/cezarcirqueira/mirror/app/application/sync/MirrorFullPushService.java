package br.com.cezarcirqueira.mirror.app.application.sync;

import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.FileIndexEntryRepository;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.LocalReplicaRepository;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.MirrorDefinitionRepository;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.PeerEndpointRepository;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity.FileIndexEntryEntity;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity.LocalReplicaEntity;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity.MirrorDefinitionEntity;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity.PeerEndpointEntity;
import br.com.cezarcirqueira.mirror.app.adapter.out.peer.PeerMirrorHttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MirrorFullPushService {

    private static final Logger log = LoggerFactory.getLogger(MirrorFullPushService.class);

    private final MirrorDefinitionRepository mirrorDefinitionRepository;
    private final LocalReplicaRepository localReplicaRepository;
    private final PeerEndpointRepository peerEndpointRepository;
    private final FileIndexEntryRepository fileIndexEntryRepository;
    private final PeerMirrorHttpClient peerMirrorHttpClient;

    public MirrorFullPushService(
            MirrorDefinitionRepository mirrorDefinitionRepository,
            LocalReplicaRepository localReplicaRepository,
            PeerEndpointRepository peerEndpointRepository,
            FileIndexEntryRepository fileIndexEntryRepository,
            PeerMirrorHttpClient peerMirrorHttpClient) {
        this.mirrorDefinitionRepository = mirrorDefinitionRepository;
        this.localReplicaRepository = localReplicaRepository;
        this.peerEndpointRepository = peerEndpointRepository;
        this.fileIndexEntryRepository = fileIndexEntryRepository;
        this.peerMirrorHttpClient = peerMirrorHttpClient;
    }

    public void pushLocalSnapshotAuthoritative(String mirrorGuid) {
        MirrorDefinitionEntity mirror = mirrorDefinitionRepository.findById(mirrorGuid).orElseThrow();
        LocalReplicaEntity replica = localReplicaRepository.findByMirrorGuid(mirrorGuid).orElseThrow();
        Path root = Path.of(replica.getRootPath());
        List<FileIndexEntryEntity> rows = fileIndexEntryRepository.findByIdMirrorGuid(mirrorGuid);
        Set<String> localPaths = new HashSet<>();
        for (FileIndexEntryEntity row : rows) {
            localPaths.add(row.getId().getRelativePath());
        }
        List<PeerEndpointEntity> peers = peerEndpointRepository.findAll();
        if (peers.isEmpty()) {
            return;
        }
        for (PeerEndpointEntity peer : peers) {
            try {
                List<PeerMirrorHttpClient.ManifestEntryResponse> remote =
                        peerMirrorHttpClient.fetchManifest(peer.getBaseUrl(), mirrorGuid, mirror.getSharedSecret());
                for (PeerMirrorHttpClient.ManifestEntryResponse remoteRow : remote) {
                    if (!localPaths.contains(remoteRow.relativePath())) {
                        peerMirrorHttpClient.deleteFile(
                                peer.getBaseUrl(), mirrorGuid, mirror.getSharedSecret(), remoteRow.relativePath());
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to reconcile remote deletions for peer {}", peer.getBaseUrl(), e);
            }
        }
        for (PeerEndpointEntity peer : peers) {
            for (String rel : localPaths) {
                Path file = root.resolve(rel);
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                try {
                    peerMirrorHttpClient.putFile(peer.getBaseUrl(), mirrorGuid, mirror.getSharedSecret(), rel, file);
                } catch (Exception e) {
                    log.warn("Full push failed {} {}", peer.getBaseUrl(), rel, e);
                }
            }
        }
    }
}
