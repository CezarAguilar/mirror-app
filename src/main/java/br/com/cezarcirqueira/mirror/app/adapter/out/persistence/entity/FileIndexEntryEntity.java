package br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "file_index_entry")
public class FileIndexEntryEntity {

    @EmbeddedId
    private FileIndexEntryId id;

    @Column(name = "sha256_hex", nullable = false, length = 64)
    private String sha256Hex;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "last_modified_epoch_ms", nullable = false)
    private long lastModifiedEpochMs;

    protected FileIndexEntryEntity() {}

    public FileIndexEntryEntity(FileIndexEntryId id, String sha256Hex, long sizeBytes, long lastModifiedEpochMs) {
        this.id = id;
        this.sha256Hex = sha256Hex;
        this.sizeBytes = sizeBytes;
        this.lastModifiedEpochMs = lastModifiedEpochMs;
    }

    public FileIndexEntryId getId() {
        return id;
    }

    public String getSha256Hex() {
        return sha256Hex;
    }

    public void setSha256Hex(String sha256Hex) {
        this.sha256Hex = sha256Hex;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public long getLastModifiedEpochMs() {
        return lastModifiedEpochMs;
    }

    public void setLastModifiedEpochMs(long lastModifiedEpochMs) {
        this.lastModifiedEpochMs = lastModifiedEpochMs;
    }
}
