package br.com.cezarcirqueira.mirror.service;

import br.com.cezarcirqueira.mirror.domain.AppRole;
import br.com.cezarcirqueira.mirror.domain.AppSettings;
import br.com.cezarcirqueira.mirror.repository.AppSettingsRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppStateService {

    private final AppSettingsRepository repo;

    public AppStateService(AppSettingsRepository repo) {
        this.repo = repo;
    }

    @PostConstruct
    @Transactional
    public void initialize() {
        if (!repo.existsById(1L)) {
            repo.save(new AppSettings(1L));
        }
    }

    public AppSettings getSettings() {
        return repo.findById(1L).orElseThrow(() -> new IllegalStateException("AppSettings not initialized"));
    }

    public AppRole getRole() {
        return getSettings().getRole();
    }

    public boolean isMaster() {
        return AppRole.MASTER == getRole();
    }

    public boolean isSlave() {
        return AppRole.SLAVE == getRole();
    }

    @Transactional
    public AppSettings setRoleMaster() {
        AppSettings s = getSettings();
        s.setRole(AppRole.MASTER);
        s.setMasterIp(null);
        s.setMasterName(null);
        s.setMasterPort(null);
        return repo.save(s);
    }

    @Transactional
    public AppSettings setRoleSlave(String masterIp, String masterName, Integer masterPort) {
        AppSettings s = getSettings();
        s.setRole(AppRole.SLAVE);
        s.setMasterIp(masterIp);
        s.setMasterName(masterName);
        s.setMasterPort(masterPort);
        return repo.save(s);
    }
}
