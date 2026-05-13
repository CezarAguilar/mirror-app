package br.com.cezarcirqueira.mirror.app.application.sync;

import br.com.cezarcirqueira.mirror.app.adapter.in.web.dto.LocalReplicaResponse;
import br.com.cezarcirqueira.mirror.app.adapter.in.web.dto.MergeConflictResponse;
import br.com.cezarcirqueira.mirror.app.adapter.in.web.dto.PeerEndpointResponse;
import br.com.cezarcirqueira.mirror.app.adapter.in.web.dto.RegisterReplicaRequest;
import br.com.cezarcirqueira.mirror.app.adapter.in.web.dto.ResumeMirrorRequest;
import br.com.cezarcirqueira.mirror.app.adapter.in.web.dto.ResumeMirrorResult;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.LocalReplicaRepository;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.MergeConflictRepository;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.MirrorDefinitionRepository;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.NodeSettingsRepository;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.PeerEndpointRepository;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity.LocalReplicaEntity;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity.MirrorDefinitionEntity;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity.MergeConflictEntity;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity.NodeSettingsEntity;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity.PeerEndpointEntity;
import br.com.cezarcirqueira.mirror.app.adapter.in.filesystem.RecursiveDirectoryWatchCoordinator;
import br.com.cezarcirqueira.mirror.app.domain.ConflictStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MirrorRegistryApplicationService {

    private static final String NODE_SINGLETON_KEY = "default";

    private final MirrorDefinitionRepository mirrorDefinitionRepository;
    private final LocalReplicaRepository localReplicaRepository;
    private final PeerEndpointRepository peerEndpointRepository;
    private final NodeSettingsRepository nodeSettingsRepository;
    private final MergeConflictRepository mergeConflictRepository;
    private final FileIndexService fileIndexService;
    private final PauseBaselineService pauseBaselineService;
    private final ResumeOrchestratorService resumeOrchestratorService;
    private final RecursiveDirectoryWatchCoordinator recursiveDirectoryWatchCoordinator;

    public MirrorRegistryApplicationService(
            MirrorDefinitionRepository mirrorDefinitionRepository,
            LocalReplicaRepository localReplicaRepository,
            PeerEndpointRepository peerEndpointRepository,
            NodeSettingsRepository nodeSettingsRepository,
            MergeConflictRepository mergeConflictRepository,
            FileIndexService fileIndexService,
            PauseBaselineService pauseBaselineService,
            ResumeOrchestratorService resumeOrchestratorService,
            RecursiveDirectoryWatchCoordinator recursiveDirectoryWatchCoordinator) {
        this.mirrorDefinitionRepository = mirrorDefinitionRepository;
        this.localReplicaRepository = localReplicaRepository;
        this.peerEndpointRepository = peerEndpointRepository;
        this.nodeSettingsRepository = nodeSettingsRepository;
        this.mergeConflictRepository = mergeConflictRepository;
        this.fileIndexService = fileIndexService;
        this.pauseBaselineService = pauseBaselineService;
        this.resumeOrchestratorService = resumeOrchestratorService;
        this.recursiveDirectoryWatchCoordinator = recursiveDirectoryWatchCoordinator;
    }

    @Transactional(readOnly = true)
    public String getDisplayName() {
        return nodeSettingsRepository
                .findById(NODE_SINGLETON_KEY)
                .map(NodeSettingsEntity::getDisplayName)
                .orElse("unknown");
    }

    @Transactional
    public void updateDisplayName(String displayName) {
        NodeSettingsEntity settings =
                nodeSettingsRepository.findById(NODE_SINGLETON_KEY).orElseThrow();
        settings.setDisplayName(displayName);
        nodeSettingsRepository.save(settings);
    }

    @Transactional(readOnly = true)
    public List<LocalReplicaResponse> listReplicas() {
        return localReplicaRepository.findAll().stream()
                .map(
                        r -> new LocalReplicaResponse(
                                r.getMirrorGuid(),
                                r.getRootPath(),
                                r.getMirror().isMirrorPaused(),
                                r.isReplicaPaused(),
                                mergeConflictRepository
                                        .findByMirrorGuidAndStatus(r.getMirrorGuid(), ConflictStatus.OPEN)
                                        .size()))
                .toList();
    }

    @Transactional
    public LocalReplicaResponse registerReplica(RegisterReplicaRequest request) throws IOException {
        String guid = request.mirrorGuid();
        if (guid == null || guid.isBlank()) {
            guid = UUID.randomUUID().toString();
        } else {
            UUID.fromString(guid);
        }
        String secret = request.sharedSecret();
        if (secret == null || secret.isBlank()) {
            secret = randomHex(32);
        }
        Path root = Path.of(request.rootPath());
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("rootPath must be an existing directory");
        }
        if (mirrorDefinitionRepository.existsById(guid)) {
            throw new IllegalArgumentException("Mirror GUID already exists on this node");
        }
        MirrorDefinitionEntity mirror = new MirrorDefinitionEntity(guid, secret);
        mirrorDefinitionRepository.save(mirror);
        LocalReplicaEntity replica = new LocalReplicaEntity(mirror, root.toAbsolutePath().toString());
        localReplicaRepository.save(replica);
        fileIndexService.fullRescanMirrorReturningDelta(guid);
        try {
            recursiveDirectoryWatchCoordinator.restartForMirror(guid);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start directory watch", e);
        }
        return new LocalReplicaResponse(
                guid, replica.getRootPath(), mirror.isMirrorPaused(), replica.isReplicaPaused(), 0);
    }

    @Transactional
    public void deleteReplica(String mirrorGuid) throws IOException {
        recursiveDirectoryWatchCoordinator.stopForMirror(mirrorGuid);
        mergeConflictRepository.deleteAllByMirrorGuid(mirrorGuid);
        pauseBaselineService.clearBaseline(mirrorGuid);
        fileIndexService.removeIndexForMirror(mirrorGuid);
        localReplicaRepository.deleteById(mirrorGuid);
        mirrorDefinitionRepository.deleteById(mirrorGuid);
    }

    @Transactional
    public void setMirrorPaused(String mirrorGuid, boolean paused) {
        MirrorDefinitionEntity mirror = mirrorDefinitionRepository.findById(mirrorGuid).orElseThrow();
        mirror.setMirrorPaused(paused);
        mirrorDefinitionRepository.save(mirror);
        if (paused) {
            pauseBaselineService.captureBaseline(mirrorGuid);
        }
    }

    @Transactional
    public void setReplicaPaused(String mirrorGuid, boolean paused) {
        LocalReplicaEntity replica = localReplicaRepository.findByMirrorGuid(mirrorGuid).orElseThrow();
        replica.setReplicaPaused(paused);
        localReplicaRepository.save(replica);
    }

    @Transactional
    public ResumeMirrorResult resume(String mirrorGuid, ResumeMirrorRequest request)
            throws IOException, InterruptedException {
        return resumeOrchestratorService.resume(mirrorGuid, request);
    }

    @Transactional(readOnly = true)
    public List<PeerEndpointResponse> listPeers() {
        return peerEndpointRepository.findAll().stream()
                .map(p -> new PeerEndpointResponse(p.getId(), p.getBaseUrl()))
                .toList();
    }

    @Transactional
    public PeerEndpointResponse addPeer(String baseUrl) {
        String normalized = baseUrl.trim();
        PeerEndpointEntity entity = new PeerEndpointEntity(normalized);
        PeerEndpointEntity saved = peerEndpointRepository.save(entity);
        return new PeerEndpointResponse(saved.getId(), saved.getBaseUrl());
    }

    @Transactional
    public void deletePeer(long id) {
        peerEndpointRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<MergeConflictResponse> listOpenConflicts(Optional<String> mirrorGuid) {
        List<MergeConflictEntity> rows =
                mirrorGuid
                        .map(g -> mergeConflictRepository.findByMirrorGuidAndStatus(g, ConflictStatus.OPEN))
                        .orElseGet(() -> mergeConflictRepository.findByStatus(ConflictStatus.OPEN));
        return rows.stream().map(this::toConflictDto).toList();
    }

    private MergeConflictResponse toConflictDto(MergeConflictEntity e) {
        return new MergeConflictResponse(
                e.getId(),
                e.getMirrorGuid(),
                e.getRelativePath(),
                e.getLocalHash(),
                e.getRemoteHash(),
                e.getLocalTextSnapshot(),
                e.getRemoteTextSnapshot(),
                e.getStatus().name());
    }

    private String randomHex(int bytes) {
        byte[] buf = new byte[bytes];
        new SecureRandom().nextBytes(buf);
        return HexFormat.of().formatHex(buf);
    }
}
