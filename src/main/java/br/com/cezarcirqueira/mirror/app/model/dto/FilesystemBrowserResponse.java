package br.com.cezarcirqueira.mirror.app.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FilesystemBrowserResponse {

    /** Absolute path of the directory being listed, or {@code null} when listing filesystem roots. */
    private String currentPath;

    /** Parent path, or {@code null} when {@code currentPath} is a root / when listing roots. */
    private String parentPath;

    /** Sub-directory entries available inside {@code currentPath}. */
    private List<FilesystemEntry> entries;
}
