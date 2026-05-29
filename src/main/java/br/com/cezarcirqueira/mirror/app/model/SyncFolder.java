package br.com.cezarcirqueira.mirror.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "sync_folder")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncFolder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private UUID guid;

    @Column(nullable = false)
    private LocalDateTime creationDate;

    @Column(nullable = false)
    private String basePath;

    @PrePersist
    public void prePersist() {
        if (this.guid == null) {
            this.guid = UUID.randomUUID();
        }
        if (this.creationDate == null) {
            this.creationDate = LocalDateTime.now();
        }
    }
}
