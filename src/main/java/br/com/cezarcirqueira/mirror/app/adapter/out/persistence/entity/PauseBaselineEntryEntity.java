package br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "pause_baseline_entry")
public class PauseBaselineEntryEntity {

    @EmbeddedId
    private FileIndexEntryId id;

    @Column(name = "sha256_hex", nullable = false, length = 64)
    private String sha256Hex;

    protected PauseBaselineEntryEntity() {}

    public PauseBaselineEntryEntity(FileIndexEntryId id, String sha256Hex) {
        this.id = id;
        this.sha256Hex = sha256Hex;
    }

    public FileIndexEntryId getId() {
        return id;
    }

    public String getSha256Hex() {
        return sha256Hex;
    }
}
