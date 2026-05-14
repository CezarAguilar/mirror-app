package br.com.cezarcirqueira.mirror.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;

@Entity
public class AppSettings {

    @Id
    private Long id = 1L;

    @Enumerated(EnumType.STRING)
    private AppRole role = AppRole.UNSET;

    private String masterIp;
    private String masterName;
    private Integer masterPort;

    protected AppSettings() {}

    public AppSettings(Long id) {
        this.id = id;
    }

    public Long getId() { return id; }

    public AppRole getRole() { return role; }
    public void setRole(AppRole role) { this.role = role; }

    public String getMasterIp() { return masterIp; }
    public void setMasterIp(String masterIp) { this.masterIp = masterIp; }

    public String getMasterName() { return masterName; }
    public void setMasterName(String masterName) { this.masterName = masterName; }

    public Integer getMasterPort() { return masterPort; }
    public void setMasterPort(Integer masterPort) { this.masterPort = masterPort; }
}
