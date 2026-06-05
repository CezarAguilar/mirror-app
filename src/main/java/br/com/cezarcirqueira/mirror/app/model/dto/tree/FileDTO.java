package br.com.cezarcirqueira.mirror.app.model.dto.tree;

public class FileDTO extends FileSystemNodeDTO {

    private final String hash;

    public FileDTO(String name, String path, String hash) {
        super(name, path);
        this.hash = hash;
    }

    public String getHash() {
        return hash;
    }

    @Override
    public NodeType getType() {
        return NodeType.FILE;
    }
}
