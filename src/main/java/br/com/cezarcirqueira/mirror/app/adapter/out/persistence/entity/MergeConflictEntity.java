package br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity;

import br.com.cezarcirqueira.mirror.app.domain.ConflictStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "merge_conflict")
public class MergeConflictEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mirror_guid", nullable = false, length = 36)
    private String mirrorGuid;

    @Column(name = "relative_path", nullable = false, length = 4096)
    private String relativePath;

    @Column(name = "local_hash", nullable = false, length = 64)
    private String localHash;

    @Column(name = "remote_hash", nullable = false, length = 64)
    private String remoteHash;

    @Column(name = "local_text_snapshot", columnDefinition = "TEXT")
    private String localTextSnapshot;

    @Column(name = "remote_text_snapshot", columnDefinition = "TEXT")
    private String remoteTextSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ConflictStatus status;

    protected MergeConflictEntity() {}

    public MergeConflictEntity(
            String mirrorGuid,
            String relativePath,
            String localHash,
            String remoteHash,
            String localTextSnapshot,
            String remoteTextSnapshot,
            ConflictStatus status) {
        this.mirrorGuid = mirrorGuid;
        this.relativePath = relativePath;
        this.localHash = localHash;
        this.remoteHash = remoteHash;
        this.localTextSnapshot = localTextSnapshot;
        this.remoteTextSnapshot = remoteTextSnapshot;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public String getMirrorGuid() {
        return mirrorGuid;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public String getLocalHash() {
        return localHash;
    }

    public String getRemoteHash() {
        return remoteHash;
    }

    public String getLocalTextSnapshot() {
        return localTextSnapshot;
    }

    public String getRemoteTextSnapshot() {
        return remoteTextSnapshot;
    }

    public ConflictStatus getStatus() {
        return status;
    }

    public void setStatus(ConflictStatus status) {
        this.status = status;
    }
}
