package br.com.cezarcirqueira.mirror.app.adapter.in.web;

import br.com.cezarcirqueira.mirror.app.adapter.in.web.dto.LocalReplicaResponse;
import br.com.cezarcirqueira.mirror.app.adapter.in.web.dto.MergeConflictResponse;
import br.com.cezarcirqueira.mirror.app.adapter.in.web.dto.MirrorPauseRequest;
import br.com.cezarcirqueira.mirror.app.adapter.in.web.dto.NodeIdentityResponse;
import br.com.cezarcirqueira.mirror.app.adapter.in.web.dto.PeerEndpointRequest;
import br.com.cezarcirqueira.mirror.app.adapter.in.web.dto.PeerEndpointResponse;
import br.com.cezarcirqueira.mirror.app.adapter.in.web.dto.RegisterReplicaRequest;
import br.com.cezarcirqueira.mirror.app.adapter.in.web.dto.ResolveMergeConflictRequest;
import br.com.cezarcirqueira.mirror.app.adapter.in.web.dto.ResumeMirrorRequest;
import br.com.cezarcirqueira.mirror.app.adapter.in.web.dto.ResumeMirrorResult;
import br.com.cezarcirqueira.mirror.app.adapter.in.web.dto.UpdateNodeIdentityRequest;
import br.com.cezarcirqueira.mirror.app.application.sync.MergeConflictApplicationService;
import br.com.cezarcirqueira.mirror.app.application.sync.MirrorRegistryApplicationService;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/management")
public class MirrorManagementRestController {

    private final MirrorRegistryApplicationService mirrorRegistryApplicationService;
    private final MergeConflictApplicationService mergeConflictApplicationService;

    public MirrorManagementRestController(
            MirrorRegistryApplicationService mirrorRegistryApplicationService,
            MergeConflictApplicationService mergeConflictApplicationService) {
        this.mirrorRegistryApplicationService = mirrorRegistryApplicationService;
        this.mergeConflictApplicationService = mergeConflictApplicationService;
    }

    @GetMapping("/node")
    public NodeIdentityResponse getNode() {
        return new NodeIdentityResponse(mirrorRegistryApplicationService.getDisplayName());
    }

    @PutMapping("/node")
    public ResponseEntity<Void> updateNode(@RequestBody UpdateNodeIdentityRequest request) {
        mirrorRegistryApplicationService.updateDisplayName(request.displayName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/replicas")
    public List<LocalReplicaResponse> listReplicas() {
        return mirrorRegistryApplicationService.listReplicas();
    }

    @PostMapping("/replicas")
    public LocalReplicaResponse registerReplica(@RequestBody RegisterReplicaRequest request) throws IOException {
        return mirrorRegistryApplicationService.registerReplica(request);
    }

    @DeleteMapping("/replicas/{mirrorGuid}")
    public ResponseEntity<Void> deleteReplica(@PathVariable String mirrorGuid) throws IOException {
        mirrorRegistryApplicationService.deleteReplica(mirrorGuid);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/replicas/{mirrorGuid}/mirror-pause")
    public ResponseEntity<Void> setMirrorPause(
            @PathVariable String mirrorGuid, @RequestBody MirrorPauseRequest request) {
        mirrorRegistryApplicationService.setMirrorPaused(mirrorGuid, request.paused());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/replicas/{mirrorGuid}/replica-pause")
    public ResponseEntity<Void> setReplicaPause(
            @PathVariable String mirrorGuid, @RequestBody MirrorPauseRequest request) {
        mirrorRegistryApplicationService.setReplicaPaused(mirrorGuid, request.paused());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/replicas/{mirrorGuid}/resume")
    public ResumeMirrorResult resume(@PathVariable String mirrorGuid, @RequestBody ResumeMirrorRequest request)
            throws IOException, InterruptedException {
        return mirrorRegistryApplicationService.resume(mirrorGuid, request);
    }

    @GetMapping("/peers")
    public List<PeerEndpointResponse> listPeers() {
        return mirrorRegistryApplicationService.listPeers();
    }

    @PostMapping("/peers")
    public PeerEndpointResponse addPeer(@RequestBody PeerEndpointRequest request) {
        return mirrorRegistryApplicationService.addPeer(request.baseUrl());
    }

    @DeleteMapping("/peers/{id}")
    public ResponseEntity<Void> deletePeer(@PathVariable long id) {
        mirrorRegistryApplicationService.deletePeer(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/conflicts")
    public List<MergeConflictResponse> listConflicts(@RequestParam(name = "mirrorGuid", required = false) String mirrorGuid) {
        return mirrorRegistryApplicationService.listOpenConflicts(Optional.ofNullable(mirrorGuid));
    }

    @PostMapping("/conflicts/{id}/resolve")
    public ResponseEntity<Void> resolveConflict(
            @PathVariable long id, @RequestBody ResolveMergeConflictRequest request)
            throws IOException, InterruptedException {
        mergeConflictApplicationService.resolve(id, request);
        return ResponseEntity.noContent().build();
    }
}
