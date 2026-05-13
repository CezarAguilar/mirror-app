package br.com.cezarcirqueira.mirror.app.application.sync;

import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.FileIndexEntryRepository;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.LocalReplicaRepository;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.MirrorDefinitionRepository;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.PeerEndpointRepository;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity.LocalReplicaEntity;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity.MirrorDefinitionEntity;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity.PeerEndpointEntity;
import br.com.cezarcirqueira.mirror.app.adapter.out.peer.PeerMirrorHttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OutboundSyncService {

    private static final Logger log = LoggerFactory.getLogger(OutboundSyncService.class);

    private final MirrorDefinitionRepository mirrorDefinitionRepository;
    private final LocalReplicaRepository localReplicaRepository;
    private final PeerEndpointRepository peerEndpointRepository;
    private final PeerMirrorHttpClient peerMirrorHttpClient;

    public OutboundSyncService(
            MirrorDefinitionRepository mirrorDefinitionRepository,
            LocalReplicaRepository localReplicaRepository,
            PeerEndpointRepository peerEndpointRepository,
            PeerMirrorHttpClient peerMirrorHttpClient) {
        this.mirrorDefinitionRepository = mirrorDefinitionRepository;
        this.localReplicaRepository = localReplicaRepository;
        this.peerEndpointRepository = peerEndpointRepository;
        this.peerMirrorHttpClient = peerMirrorHttpClient;
    }

    public void pushDeltaAfterRescan(
            String mirrorGuid, RescanDelta delta, RecentRemoteWriteRegistry registry) {
        MirrorDefinitionEntity mirror = mirrorDefinitionRepository.findById(mirrorGuid).orElse(null);
        if (mirror == null || mirror.isMirrorPaused()) {
            return;
        }
        LocalReplicaEntity replica = localReplicaRepository.findByMirrorGuid(mirrorGuid).orElse(null);
        if (replica == null || replica.isReplicaPaused()) {
            return;
        }
        if (delta.isEmpty()) {
            return;
        }
        pushDelta(mirrorGuid, mirror.getSharedSecret(), replica, delta, registry);
    }

    private void pushDelta(
            String mirrorGuid,
            String secret,
            LocalReplicaEntity replica,
            RescanDelta delta,
            RecentRemoteWriteRegistry registry) {
        Path root = Path.of(replica.getRootPath());
        List<PeerEndpointEntity> peers = peerEndpointRepository.findAll();
        if (peers.isEmpty()) {
            return;
        }
        for (String rel : delta.deletedRelativePaths()) {
            if (registry.isRecentRemoteWrite(mirrorGuid, rel)) {
                continue;
            }
            for (PeerEndpointEntity peer : peers) {
                try {
                    peerMirrorHttpClient.deleteFile(peer.getBaseUrl(), mirrorGuid, secret, rel);
                } catch (Exception e) {
                    log.warn("Peer delete failed {} {}", peer.getBaseUrl(), rel, e);
                }
            }
        }
        for (String rel : delta.upsertedOrChangedRelativePaths()) {
            if (registry.isRecentRemoteWrite(mirrorGuid, rel)) {
                continue;
            }
            Path file = root.resolve(rel);
            if (!Files.isRegularFile(file)) {
                continue;
            }
            for (PeerEndpointEntity peer : peers) {
                try {
                    peerMirrorHttpClient.putFile(peer.getBaseUrl(), mirrorGuid, secret, rel, file);
                } catch (Exception e) {
                    log.warn("Peer upload failed {} {}", peer.getBaseUrl(), rel, e);
                }
            }
        }
    }
}
