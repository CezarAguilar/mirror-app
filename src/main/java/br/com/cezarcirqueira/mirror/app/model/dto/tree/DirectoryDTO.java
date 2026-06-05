package br.com.cezarcirqueira.mirror.app.model.dto.tree;

import java.util.List;

public class DirectoryDTO extends FileSystemNodeDTO {

    private final List<FileSystemNodeDTO> children;

    public DirectoryDTO(String name, String path, List<FileSystemNodeDTO> children) {
        super(name, path);
        this.children = children;
    }

    public List<FileSystemNodeDTO> getChildren() {
        return children;
    }

    @Override
    public NodeType getType() {
        return NodeType.DIRECTORY;
    }
}
