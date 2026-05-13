package br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity;

import br.com.cezarcirqueira.mirror.app.domain.ResumeStrategy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "mirror_definition")
public class MirrorDefinitionEntity {

    @Id
    @Column(name = "mirror_guid", length = 36, nullable = false)
    private String mirrorGuid;

    @Column(name = "shared_secret", nullable = false, length = 512)
    private String sharedSecret;

    @Column(name = "mirror_paused", nullable = false)
    private boolean mirrorPaused;

    /**
     * When resuming after pause, user-selected strategy (null if not awaiting resume).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "pending_resume_strategy", length = 32)
    private ResumeStrategy pendingResumeStrategy;

    protected MirrorDefinitionEntity() {}

    public MirrorDefinitionEntity(String mirrorGuid, String sharedSecret) {
        this.mirrorGuid = mirrorGuid;
        this.sharedSecret = sharedSecret;
        this.mirrorPaused = false;
    }

    public String getMirrorGuid() {
        return mirrorGuid;
    }

    public String getSharedSecret() {
        return sharedSecret;
    }

    public void setSharedSecret(String sharedSecret) {
        this.sharedSecret = sharedSecret;
    }

    public boolean isMirrorPaused() {
        return mirrorPaused;
    }

    public void setMirrorPaused(boolean mirrorPaused) {
        this.mirrorPaused = mirrorPaused;
    }

    public ResumeStrategy getPendingResumeStrategy() {
        return pendingResumeStrategy;
    }

    public void setPendingResumeStrategy(ResumeStrategy pendingResumeStrategy) {
        this.pendingResumeStrategy = pendingResumeStrategy;
    }
}
