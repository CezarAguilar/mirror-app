package br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "node_settings")
public class NodeSettingsEntity {

    @Id
    @Column(name = "singleton_key", nullable = false, length = 16)
    private String singletonKey = "default";

    @Column(name = "display_name", nullable = false, length = 256)
    private String displayName;

    protected NodeSettingsEntity() {}

    public NodeSettingsEntity(String displayName) {
        this.displayName = displayName;
    }

    public String getSingletonKey() {
        return singletonKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
