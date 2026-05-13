package br.com.cezarcirqueira.mirror.app.application.sync;

import br.com.cezarcirqueira.mirror.app.adapter.in.web.dto.ResolveMergeConflictRequest;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.LocalReplicaRepository;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.MergeConflictRepository;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.MirrorDefinitionRepository;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.PeerEndpointRepository;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity.LocalReplicaEntity;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity.MergeConflictEntity;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity.PeerEndpointEntity;
import br.com.cezarcirqueira.mirror.app.adapter.out.peer.PeerMirrorHttpClient;
import br.com.cezarcirqueira.mirror.app.domain.ConflictStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MergeConflictApplicationService {

    private final MergeConflictRepository mergeConflictRepository;
    private final LocalReplicaFileWriter localReplicaFileWriter;
    private final MirrorDefinitionRepository mirrorDefinitionRepository;
    private final PauseBaselineService pauseBaselineService;
    private final OutboundSyncService outboundSyncService;
    private final RecentRemoteWriteRegistry recentRemoteWriteRegistry;
    private final LocalReplicaRepository localReplicaRepository;
    private final PeerEndpointRepository peerEndpointRepository;
    private final PeerMirrorHttpClient peerMirrorHttpClient;

    public MergeConflictApplicationService(
            MergeConflictRepository mergeConflictRepository,
            LocalReplicaFileWriter localReplicaFileWriter,
            MirrorDefinitionRepository mirrorDefinitionRepository,
            PauseBaselineService pauseBaselineService,
            OutboundSyncService outboundSyncService,
            RecentRemoteWriteRegistry recentRemoteWriteRegistry,
            LocalReplicaRepository localReplicaRepository,
            PeerEndpointRepository peerEndpointRepository,
            PeerMirrorHttpClient peerMirrorHttpClient) {
        this.mergeConflictRepository = mergeConflictRepository;
        this.localReplicaFileWriter = localReplicaFileWriter;
        this.mirrorDefinitionRepository = mirrorDefinitionRepository;
        this.pauseBaselineService = pauseBaselineService;
        this.outboundSyncService = outboundSyncService;
        this.recentRemoteWriteRegistry = recentRemoteWriteRegistry;
        this.localReplicaRepository = localReplicaRepository;
        this.peerEndpointRepository = peerEndpointRepository;
        this.peerMirrorHttpClient = peerMirrorHttpClient;
    }

    @Transactional
    public void resolve(long conflictId, ResolveMergeConflictRequest request) throws IOException, InterruptedException {
        MergeConflictEntity entity = mergeConflictRepository.findById(conflictId).orElseThrow();
        String mirrorGuid = entity.getMirrorGuid();
        String path = entity.getRelativePath();

        switch (request.resolution()) {
            case LOCAL -> {
                LocalReplicaEntity replica = localReplicaRepository.findByMirrorGuid(mirrorGuid).orElseThrow();
                Path file = Path.of(replica.getRootPath()).resolve(path).normalize();
                byte[] bytes = Files.readAllBytes(file);
                localReplicaFileWriter.writeRelativeFile(mirrorGuid, path, bytes);
            }
            case REMOTE -> {
                var mirror = mirrorDefinitionRepository.findById(mirrorGuid).orElseThrow();
                PeerEndpointEntity peer = peerEndpointRepository.findAll().getFirst();
                byte[] bytes = peerMirrorHttpClient.downloadFile(peer.getBaseUrl(), mirrorGuid, mirror.getSharedSecret(), path);
                localReplicaFileWriter.writeRelativeFile(mirrorGuid, path, bytes);
            }
            case CUSTOM -> {
                if (request.customContent() == null) {
                    throw new IllegalArgumentException("customContent is required for CUSTOM resolution");
                }
                localReplicaFileWriter.writeRelativeFile(
                        mirrorGuid, path, request.customContent().getBytes(StandardCharsets.UTF_8));
            }
        }
        entity.setStatus(ConflictStatus.RESOLVED);
        mergeConflictRepository.save(entity);

        RescanDelta delta = new RescanDelta(java.util.List.of(path), java.util.List.of());
        outboundSyncService.pushDeltaAfterRescan(mirrorGuid, delta, recentRemoteWriteRegistry);

        int open = mergeConflictRepository.findByMirrorGuidAndStatus(mirrorGuid, ConflictStatus.OPEN).size();
        if (open == 0) {
            var mirror = mirrorDefinitionRepository.findById(mirrorGuid).orElseThrow();
            mirror.setMirrorPaused(false);
            mirrorDefinitionRepository.save(mirror);
            pauseBaselineService.clearBaseline(mirrorGuid);
        }
    }
}
