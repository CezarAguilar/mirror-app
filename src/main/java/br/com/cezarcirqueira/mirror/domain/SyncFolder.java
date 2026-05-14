package br.com.cezarcirqueira.mirror.domain;

import jakarta.persistence.*;

@Entity
public class SyncFolder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String guid;

    @Column(nullable = false)
    private String folderPath;

    public Long getId() { return id; }

    public String getGuid() { return guid; }
    public void setGuid(String guid) { this.guid = guid; }

    public String getFolderPath() { return folderPath; }
    public void setFolderPath(String folderPath) { this.folderPath = folderPath; }
}
