package br.com.cezarcirqueira.mirror.app.application.sync;

import br.com.cezarcirqueira.mirror.app.adapter.in.web.dto.ResumeMirrorRequest;
import br.com.cezarcirqueira.mirror.app.adapter.in.web.dto.ResumeMirrorResult;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.LocalReplicaRepository;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.MergeConflictRepository;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.MirrorDefinitionRepository;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.PauseBaselineEntryRepository;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.PeerEndpointRepository;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity.LocalReplicaEntity;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity.MergeConflictEntity;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity.PauseBaselineEntryEntity;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity.PeerEndpointEntity;
import br.com.cezarcirqueira.mirror.app.adapter.out.peer.PeerMirrorHttpClient;
import br.com.cezarcirqueira.mirror.app.application.filesystem.EditableFileClassifier;
import br.com.cezarcirqueira.mirror.app.application.filesystem.TextSnapshotReader;
import br.com.cezarcirqueira.mirror.app.domain.ConflictStatus;
import br.com.cezarcirqueira.mirror.app.domain.ResumeStrategy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResumeOrchestratorService {

    private final MirrorDefinitionRepository mirrorDefinitionRepository;
    private final LocalReplicaRepository localReplicaRepository;
    private final PauseBaselineEntryRepository pauseBaselineEntryRepository;
    private final FileIndexService fileIndexService;
    private final PeerEndpointRepository peerEndpointRepository;
    private final PeerMirrorHttpClient peerMirrorHttpClient;
    private final PauseBaselineService pauseBaselineService;
    private final MergeConflictRepository mergeConflictRepository;
    private final MirrorFullPushService mirrorFullPushService;
    private final MirrorFullPullService mirrorFullPullService;
    private final LocalReplicaFileWriter localReplicaFileWriter;
    private final OutboundSyncService outboundSyncService;
    private final RecentRemoteWriteRegistry recentRemoteWriteRegistry;
    private final EditableFileClassifier editableFileClassifier;
    private final TextSnapshotReader textSnapshotReader;

    public ResumeOrchestratorService(
            MirrorDefinitionRepository mirrorDefinitionRepository,
            LocalReplicaRepository localReplicaRepository,
            PauseBaselineEntryRepository pauseBaselineEntryRepository,
            FileIndexService fileIndexService,
            PeerEndpointRepository peerEndpointRepository,
            PeerMirrorHttpClient peerMirrorHttpClient,
            PauseBaselineService pauseBaselineService,
            MergeConflictRepository mergeConflictRepository,
            MirrorFullPushService mirrorFullPushService,
            MirrorFullPullService mirrorFullPullService,
            LocalReplicaFileWriter localReplicaFileWriter,
            OutboundSyncService outboundSyncService,
            RecentRemoteWriteRegistry recentRemoteWriteRegistry,
            EditableFileClassifier editableFileClassifier,
            TextSnapshotReader textSnapshotReader) {
        this.mirrorDefinitionRepository = mirrorDefinitionRepository;
        this.localReplicaRepository = localReplicaRepository;
        this.pauseBaselineEntryRepository = pauseBaselineEntryRepository;
        this.fileIndexService = fileIndexService;
        this.peerEndpointRepository = peerEndpointRepository;
        this.peerMirrorHttpClient = peerMirrorHttpClient;
        this.pauseBaselineService = pauseBaselineService;
        this.mergeConflictRepository = mergeConflictRepository;
        this.mirrorFullPushService = mirrorFullPushService;
        this.mirrorFullPullService = mirrorFullPullService;
        this.localReplicaFileWriter = localReplicaFileWriter;
        this.outboundSyncService = outboundSyncService;
        this.recentRemoteWriteRegistry = recentRemoteWriteRegistry;
        this.editableFileClassifier = editableFileClassifier;
        this.textSnapshotReader = textSnapshotReader;
    }

    @Transactional
    public ResumeMirrorResult resume(String mirrorGuid, ResumeMirrorRequest request)
            throws IOException, InterruptedException {
        var mirror = mirrorDefinitionRepository.findById(mirrorGuid).orElseThrow();
        if (!mirror.isMirrorPaused()) {
            throw new IllegalStateException("Mirror is not paused");
        }
        fileIndexService.fullRescanMirrorReturningDelta(mirrorGuid);

        if (request.strategy() == ResumeStrategy.OVERWRITE_ALL) {
            if (request.overwriteAuthorityOrDefault() == ResumeMirrorRequest.OverwriteAuthority.LOCAL) {
                mirrorFullPushService.pushLocalSnapshotAuthoritative(mirrorGuid);
            } else {
                mirrorFullPullService.pullRemoteSnapshotAuthoritative(mirrorGuid);
            }
            mirror.setMirrorPaused(false);
            mirrorDefinitionRepository.save(mirror);
            pauseBaselineService.clearBaseline(mirrorGuid);
            mergeConflictRepository.deleteAllByMirrorGuid(mirrorGuid);
            return new ResumeMirrorResult(true, 0, "Resumed with overwrite");
        }

        mergeConflictRepository.deleteAllByMirrorGuid(mirrorGuid);

        Map<String, String> baseline = new HashMap<>();
        for (PauseBaselineEntryEntity row : pauseBaselineEntryRepository.findByIdMirrorGuid(mirrorGuid)) {
            baseline.put(row.getId().getRelativePath(), row.getSha256Hex());
        }
        Map<String, String> local = RescanDelta.toHashMap(fileIndexService.listIndex(mirrorGuid));
        Map<String, String> remote = fetchRemoteManifestMap(mirrorGuid);

        Set<String> union = new HashSet<>();
        union.addAll(baseline.keySet());
        union.addAll(local.keySet());
        union.addAll(remote.keySet());

        for (String path : union) {
            String b = baseline.get(path);
            String l = local.get(path);
            String r = remote.get(path);
            if (Objects.equals(l, r)) {
                continue;
            }
            if (b != null && Objects.equals(l, b) && !Objects.equals(r, b)) {
                applyRemoteVersion(mirrorGuid, path, r);
                continue;
            }
            if (b != null && Objects.equals(r, b) && !Objects.equals(l, b)) {
                pushLocalVersionToPeers(mirrorGuid, path);
                continue;
            }
            if (l == null && r != null) {
                applyRemoteVersion(mirrorGuid, path, r);
                continue;
            }
            if (r == null && l != null) {
                pushLocalVersionToPeers(mirrorGuid, path);
                continue;
            }
            createConflict(mirrorGuid, path, l, r);
        }

        fileIndexService.fullRescanMirrorReturningDelta(mirrorGuid);
        int open = mergeConflictRepository.findByMirrorGuidAndStatus(mirrorGuid, ConflictStatus.OPEN).size();
        if (open == 0) {
            mirror.setMirrorPaused(false);
            mirrorDefinitionRepository.save(mirror);
            pauseBaselineService.clearBaseline(mirrorGuid);
            return new ResumeMirrorResult(true, 0, "Resumed with merge (no conflicts)");
        }
        mirrorDefinitionRepository.save(mirror);
        return new ResumeMirrorResult(false, open, "Conflicts require manual resolution");
    }

    private Map<String, String> fetchRemoteManifestMap(String mirrorGuid) throws IOException, InterruptedException {
        var mirror = mirrorDefinitionRepository.findById(mirrorGuid).orElseThrow();
        List<PeerEndpointEntity> peers = peerEndpointRepository.findAll();
        if (peers.isEmpty()) {
            throw new IllegalStateException("No peers configured");
        }
        PeerEndpointEntity peer = peers.getFirst();
        List<PeerMirrorHttpClient.ManifestEntryResponse> manifest =
                peerMirrorHttpClient.fetchManifest(peer.getBaseUrl(), mirrorGuid, mirror.getSharedSecret());
        Map<String, String> map = new HashMap<>();
        for (PeerMirrorHttpClient.ManifestEntryResponse row : manifest) {
            map.put(row.relativePath(), row.sha256Hex());
        }
        return map;
    }

    private void applyRemoteVersion(String mirrorGuid, String path, String remoteHash)
            throws IOException, InterruptedException {
        if (remoteHash == null) {
            localReplicaFileWriter.deleteRelativeFile(mirrorGuid, path);
            return;
        }
        var mirror = mirrorDefinitionRepository.findById(mirrorGuid).orElseThrow();
        PeerEndpointEntity peer = peerEndpointRepository.findAll().getFirst();
        byte[] bytes = peerMirrorHttpClient.downloadFile(peer.getBaseUrl(), mirrorGuid, mirror.getSharedSecret(), path);
        localReplicaFileWriter.writeRelativeFile(mirrorGuid, path, bytes);
    }

    private void pushLocalVersionToPeers(String mirrorGuid, String path) {
        RescanDelta delta = new RescanDelta(List.of(path), List.of());
        outboundSyncService.pushDeltaAfterRescan(mirrorGuid, delta, recentRemoteWriteRegistry);
    }

    private void createConflict(String mirrorGuid, String path, String localHash, String remoteHash)
            throws IOException, InterruptedException {
        Path rel = Path.of(path);
        boolean editable = editableFileClassifier.isEditable(rel);
        LocalReplicaEntity replica = localReplicaRepository.findByMirrorGuid(mirrorGuid).orElseThrow();
        Path root = Path.of(replica.getRootPath());
        Path localFile = root.resolve(rel).normalize();

        String localSnap = "";
        if (localHash != null && Files.isRegularFile(localFile)) {
            localSnap = textSnapshotReader.readLimited(localFile, editable).orElse("");
        }

        String remoteSnap = "";
        if (remoteHash != null) {
            PeerEndpointEntity peer = peerEndpointRepository.findAll().getFirst();
            var mirrorDefinition = mirrorDefinitionRepository.findById(mirrorGuid).orElseThrow();
            byte[] bytes = peerMirrorHttpClient.downloadFile(
                    peer.getBaseUrl(), mirrorGuid, mirrorDefinition.getSharedSecret(), path);
            if (editable) {
                String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                if (text.length() > 200_000) {
                    remoteSnap = text.substring(0, 200_000) + "\n(truncated)";
                } else {
                    remoteSnap = text;
                }
            } else {
                remoteSnap = "(binary file)";
            }
        }

        mergeConflictRepository.save(
                new MergeConflictEntity(
                        mirrorGuid,
                        path,
                        Optional.ofNullable(localHash).orElse(""),
                        Optional.ofNullable(remoteHash).orElse(""),
                        localSnap,
                        remoteSnap,
                        ConflictStatus.OPEN));
    }
}