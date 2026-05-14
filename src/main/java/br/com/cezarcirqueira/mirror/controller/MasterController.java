package br.com.cezarcirqueira.mirror.controller;

import br.com.cezarcirqueira.mirror.domain.SlaveDevice;
import br.com.cezarcirqueira.mirror.domain.SyncFolder;
import br.com.cezarcirqueira.mirror.service.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class MasterController {

    private final SlaveRegistryService slaveRegistryService;
    private final FolderRegistryService folderRegistryService;
    private final FileWatcherService fileWatcherService;
    private final AppStateService appStateService;
    private final NetworkInfoService networkInfoService;

    public MasterController(SlaveRegistryService slaveRegistryService,
                             FolderRegistryService folderRegistryService,
                             FileWatcherService fileWatcherService,
                             AppStateService appStateService,
                             NetworkInfoService networkInfoService) {
        this.slaveRegistryService = slaveRegistryService;
        this.folderRegistryService = folderRegistryService;
        this.fileWatcherService = fileWatcherService;
        this.appStateService = appStateService;
        this.networkInfoService = networkInfoService;
    }

    // ---- Device management (master UI) ----

    @GetMapping("/api/master/devices")
    public ResponseEntity<List<Map<String, Object>>> listDevices() {
        return ResponseEntity.ok(slaveRegistryService.listAll().stream()
                .map(d -> Map.<String, Object>of(
                        "id", d.getId(),
                        "name", d.getName(),
                        "ipAddress", d.getIpAddress(),
                        "port", d.getPort() != null ? d.getPort() : 0,
                        "status", d.getStatus().name()
                )).collect(Collectors.toList()));
    }

    @PostMapping("/api/master/devices/{id}/pause")
    public ResponseEntity<Void> pauseDevice(@PathVariable Long id) {
        slaveRegistryService.pauseDevice(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/master/devices/{id}/reactivate")
    public ResponseEntity<Void> reactivateDevice(@PathVariable Long id) {
        slaveRegistryService.reactivateDevice(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/api/master/devices/{id}")
    public ResponseEntity<Void> disconnectDevice(@PathVariable Long id) {
        slaveRegistryService.disconnectDevice(id);
        return ResponseEntity.ok().build();
    }

    // ---- Folder management (master UI) ----

    @GetMapping("/api/master/folders")
    public ResponseEntity<List<Map<String, Object>>> listFolders() {
        return ResponseEntity.ok(folderRegistryService.listMasterFolders().stream()
                .map(f -> Map.<String, Object>of(
                        "guid", f.getGuid(),
                        "folderPath", f.getFolderPath()
                )).collect(Collectors.toList()));
    }

    @PostMapping("/api/master/folders")
    public ResponseEntity<Map<String, Object>> addFolder(@RequestBody FolderRequest request) {
        if (request.folderPath() == null || request.folderPath().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        SyncFolder folder = folderRegistryService.addMasterFolder(request.folderPath());
        fileWatcherService.startWatching(folder.getGuid(), folder.getFolderPath());
        return ResponseEntity.ok(Map.of("guid", folder.getGuid(), "folderPath", folder.getFolderPath()));
    }

    @DeleteMapping("/api/master/folders/{guid}")
    public ResponseEntity<Void> removeFolder(@PathVariable String guid) {
        fileWatcherService.stopWatching(guid);
        folderRegistryService.removeMasterFolder(guid);
        return ResponseEntity.ok().build();
    }

    // ---- Peer endpoints (called by slaves) ----

    @PostMapping("/api/peer/register")
    public ResponseEntity<Void> registerSlave(@RequestBody SlaveRegisterRequest request) {
        slaveRegistryService.registerOrUpdate(request.name(), request.ip(), request.port());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/peer/heartbeat")
    public ResponseEntity<Void> heartbeat(@RequestBody HeartbeatRequest request) {
        slaveRegistryService.recordHeartbeat(request.ip());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/api/peer/folders")
    public ResponseEntity<List<Map<String, Object>>> peerFolders() {
        return ResponseEntity.ok(folderRegistryService.listMasterFolders().stream()
                .map(f -> Map.<String, Object>of(
                        "guid", f.getGuid(),
                        "folderPath", f.getFolderPath()
                )).collect(Collectors.toList()));
    }

    // ---- Directory browser ----

    @GetMapping("/api/files/browse")
    public ResponseEntity<List<Map<String, Object>>> browseDirectory(
            @RequestParam(required = false, defaultValue = "") String path) {

        File dir;
        if (path.isBlank()) {
            dir = File.listRoots()[0];
        } else {
            dir = new File(path);
        }

        if (!dir.exists() || !dir.isDirectory()) {
            return ResponseEntity.badRequest().build();
        }

        File[] children = dir.listFiles();
        List<Map<String, Object>> result = children == null ? List.of() :
                Arrays.stream(children)
                        .filter(File::isDirectory)
                        .map(f -> Map.<String, Object>of(
                                "name", f.getName(),
                                "path", f.getAbsolutePath(),
                                "isDirectory", true
                        ))
                        .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // ---- Slave folder mapping (slave UI) ----

    @GetMapping("/api/slave/mappings")
    public ResponseEntity<List<Map<String, Object>>> listMappings() {
        return ResponseEntity.ok(folderRegistryService.listSlaveMappings().stream()
                .map(m -> Map.<String, Object>of(
                        "guid", m.getGuid(),
                        "remoteAddress", m.getRemoteAddress() != null ? m.getRemoteAddress() : "",
                        "localPath", m.getLocalPath() != null ? m.getLocalPath() : ""
                )).collect(Collectors.toList()));
    }

    @PutMapping("/api/slave/mappings/{guid}/local")
    public ResponseEntity<Void> setLocalPath(@PathVariable String guid, @RequestBody LocalPathRequest request) {
        folderRegistryService.setLocalPath(guid, request.localPath());
        fileWatcherService.startWatching(guid, request.localPath());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/api/slave/mappings/{guid}/local")
    public ResponseEntity<Void> removeLocalPath(@PathVariable String guid) {
        fileWatcherService.stopWatching(guid);
        folderRegistryService.removeLocalPath(guid);
        return ResponseEntity.ok().build();
    }

    public record FolderRequest(String folderPath) {}
    public record SlaveRegisterRequest(String name, String ip, Integer port) {}
    public record HeartbeatRequest(String ip) {}
    public record LocalPathRequest(String localPath) {}
}
