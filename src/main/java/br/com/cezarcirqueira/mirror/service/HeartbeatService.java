package br.com.cezarcirqueira.mirror.service;

import br.com.cezarcirqueira.mirror.websocket.SlaveWebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class HeartbeatService {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatService.class);

    private final AppStateService appStateService;
    private final SlaveRegistryService slaveRegistryService;
    private final NetworkInfoService networkInfoService;
    private final RestTemplate restTemplate;
    private final SlaveWebSocketClient slaveWsClient;

    @Value("${mirror.heartbeat-timeout-seconds:15}")
    private int heartbeatTimeoutSeconds;

    public HeartbeatService(AppStateService appStateService,
                            SlaveRegistryService slaveRegistryService,
                            NetworkInfoService networkInfoService,
                            RestTemplate restTemplate,
                            SlaveWebSocketClient slaveWsClient) {
        this.appStateService = appStateService;
        this.slaveRegistryService = slaveRegistryService;
        this.networkInfoService = networkInfoService;
        this.restTemplate = restTemplate;
        this.slaveWsClient = slaveWsClient;
    }

    @Scheduled(fixedDelayString = "${mirror.heartbeat-interval-seconds:5}000")
    public void run() {
        if (appStateService.isMaster()) {
            checkSlavesHeartbeat();
        } else if (appStateService.isSlave()) {
            sendHeartbeatToMaster();
            ensureWebSocketConnected();
        }
    }

    private void checkSlavesHeartbeat() {
        slaveRegistryService.markOfflineIfStale(heartbeatTimeoutSeconds);
    }

    private void sendHeartbeatToMaster() {
        var settings = appStateService.getSettings();
        if (settings.getMasterIp() == null) return;

        String url = "http://" + settings.getMasterIp() + ":" + settings.getMasterPort() + "/api/peer/heartbeat";
        String selfIp = networkInfoService.getLocalIpV4();
        try {
            restTemplate.postForObject(url, new HeartbeatRequest(selfIp), Void.class);
        } catch (Exception e) {
            log.warn("Heartbeat to master failed: {}", e.getMessage());
        }
    }

    private void ensureWebSocketConnected() {
        var settings = appStateService.getSettings();
        if (settings.getMasterIp() == null || settings.getMasterPort() == null) return;
        slaveWsClient.ensureConnected(settings.getMasterIp(), settings.getMasterPort());
    }

    public record HeartbeatRequest(String ip) {}
}
