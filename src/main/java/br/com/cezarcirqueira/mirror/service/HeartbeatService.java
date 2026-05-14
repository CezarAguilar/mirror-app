package br.com.cezarcirqueira.mirror.service;

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

    @Value("${mirror.heartbeat-timeout-seconds:15}")
    private int heartbeatTimeoutSeconds;

    public HeartbeatService(AppStateService appStateService,
                            SlaveRegistryService slaveRegistryService,
                            NetworkInfoService networkInfoService,
                            RestTemplate restTemplate) {
        this.appStateService = appStateService;
        this.slaveRegistryService = slaveRegistryService;
        this.networkInfoService = networkInfoService;
        this.restTemplate = restTemplate;
    }

    @Scheduled(fixedDelayString = "${mirror.heartbeat-interval-seconds:5}000")
    public void run() {
        if (appStateService.isMaster()) {
            checkSlavesHeartbeat();
        } else if (appStateService.isSlave()) {
            sendHeartbeatToMaster();
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

    public record HeartbeatRequest(String ip) {}
}
