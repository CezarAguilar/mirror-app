package br.com.cezarcirqueira.mirror.controller;

import br.com.cezarcirqueira.mirror.domain.AppRole;
import br.com.cezarcirqueira.mirror.domain.AppSettings;
import br.com.cezarcirqueira.mirror.service.AppStateService;
import br.com.cezarcirqueira.mirror.service.NetworkInfoService;
import br.com.cezarcirqueira.mirror.service.SlaveReconnectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/setup")
public class SetupController {

    private final AppStateService appStateService;
    private final NetworkInfoService networkInfoService;
    private final SlaveReconnectionService slaveReconnectionService;

    public SetupController(AppStateService appStateService,
                           NetworkInfoService networkInfoService,
                           SlaveReconnectionService slaveReconnectionService) {
        this.appStateService = appStateService;
        this.networkInfoService = networkInfoService;
        this.slaveReconnectionService = slaveReconnectionService;
    }

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getInfo() {
        AppSettings settings = appStateService.getSettings();
        return ResponseEntity.ok(Map.of(
                "name", networkInfoService.getHostName(),
                "ip", networkInfoService.getLocalIpV4(),
                "role", settings.getRole().name(),
                "masterIp", settings.getMasterIp() != null ? settings.getMasterIp() : "",
                "masterName", settings.getMasterName() != null ? settings.getMasterName() : "",
                "masterPort", settings.getMasterPort() != null ? settings.getMasterPort() : 0
        ));
    }

    @PostMapping("/role")
    public ResponseEntity<Map<String, String>> setRole(@RequestBody RoleRequest request) {
        AppRole role;
        try {
            role = AppRole.valueOf(request.role());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid role: " + request.role()));
        }

        if (role == AppRole.MASTER) {
            appStateService.setRoleMaster();
        } else if (role == AppRole.SLAVE) {
            if (request.masterIp() == null || request.masterIp().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "masterIp is required for SLAVE role"));
            }
            int port = request.masterPort() != null ? request.masterPort() : 8080;
            appStateService.setRoleSlave(request.masterIp(), request.masterName(), port);
            slaveReconnectionService.reconnect();
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "Role cannot be set to UNSET"));
        }

        return ResponseEntity.ok(Map.of("status", "ok", "role", role.name()));
    }

    public record RoleRequest(String role, String masterIp, String masterName, Integer masterPort) {}
}
