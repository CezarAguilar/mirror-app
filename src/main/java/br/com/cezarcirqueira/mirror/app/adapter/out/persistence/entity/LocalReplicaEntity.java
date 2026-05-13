package br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "local_replica")
public class LocalReplicaEntity {

    @Id
    @Column(name = "mirror_guid", length = 36, nullable = false)
    private String mirrorGuid;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "mirror_guid")
    private MirrorDefinitionEntity mirror;

    @Column(name = "root_path", nullable = false, length = 2048)
    private String rootPath;

    @Column(name = "replica_paused", nullable = false)
    private boolean replicaPaused;

    protected LocalReplicaEntity() {}

    public LocalReplicaEntity(MirrorDefinitionEntity mirror, String rootPath) {
        this.mirror = mirror;
        this.mirrorGuid = mirror.getMirrorGuid();
        this.rootPath = rootPath;
        this.replicaPaused = false;
    }

    public String getMirrorGuid() {
        return mirrorGuid;
    }

    public MirrorDefinitionEntity getMirror() {
        return mirror;
    }

    public String getRootPath() {
        return rootPath;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    public boolean isReplicaPaused() {
        return replicaPaused;
    }

    public void setReplicaPaused(boolean replicaPaused) {
        this.replicaPaused = replicaPaused;
    }
}
