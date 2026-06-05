package br.com.cezarcirqueira.mirror.app.model.dto.tree;

public abstract class FileSystemNodeDTO {

    private final String name;
    private final String path;

    protected FileSystemNodeDTO(String name, String path) {
        this.name = name;
        this.path = path;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public abstract NodeType getType();
}
