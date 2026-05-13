package br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class FileIndexEntryId implements Serializable {

    @Column(name = "mirror_guid", length = 36, nullable = false)
    private String mirrorGuid;

    @Column(name = "relative_path", nullable = false, length = 4096)
    private String relativePath;

    protected FileIndexEntryId() {}

    public FileIndexEntryId(String mirrorGuid, String relativePath) {
        this.mirrorGuid = mirrorGuid;
        this.relativePath = relativePath;
    }

    public String getMirrorGuid() {
        return mirrorGuid;
    }

    public String getRelativePath() {
        return relativePath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FileIndexEntryId that = (FileIndexEntryId) o;
        return Objects.equals(mirrorGuid, that.mirrorGuid) && Objects.equals(relativePath, that.relativePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mirrorGuid, relativePath);
    }
}
