package br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "local_replica")
public class LocalReplicaEntity implements Persistable<String> {

    @Id
    @Column(name = "mirror_guid", length = 36, nullable = false)
    private String mirrorGuid;

    @OneToOne(fetch = FetchType.LAZY, optional = false, cascade = CascadeType.PERSIST)
    @MapsId
    @JoinColumn(name = "mirror_guid")
    private MirrorDefinitionEntity mirror;

    @Column(name = "root_path", nullable = false, length = 2048)
    private String rootPath;

    @Column(name = "replica_paused", nullable = false)
    private boolean replicaPaused;

    /**
     * Spring Data {@code save()} uses {@code merge()} when the id is non-null; with {@link MapsId} we assign the id
     * before persist, which incorrectly triggers merge and causes {@code StaleObjectStateException}. This flag forces
     * {@code persist()} for new rows.
     */
    @Transient
    private boolean persisted;

    protected LocalReplicaEntity() {}

    public LocalReplicaEntity(MirrorDefinitionEntity mirror, String rootPath) {
        this.mirror = mirror;
        this.mirrorGuid = mirror.getMirrorGuid();
        this.rootPath = rootPath;
        this.replicaPaused = false;
        this.persisted = false;
    }

    @PostPersist
    @PostLoad
    private void markPersisted() {
        persisted = true;
    }

    @Override
    public String getId() {
        return mirrorGuid;
    }

    @Override
    public boolean isNew() {
        return !persisted;
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
