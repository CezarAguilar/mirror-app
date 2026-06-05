package br.com.cezarcirqueira.mirror.app.sync;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
public class InstanceIdentityService {

    private String instanceId;

    @PostConstruct
    void init() {
        this.instanceId = UUID.randomUUID().toString();
        log.info("Instance identity initialised: {}", instanceId);
    }

    public String getInstanceId() {
        return instanceId;
    }
}
