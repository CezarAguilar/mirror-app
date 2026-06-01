package br.com.cezarcirqueira.mirror.app.services.impl;

import br.com.cezarcirqueira.mirror.app.exceptions.ResourceNotFoundException;
import br.com.cezarcirqueira.mirror.app.model.dto.FilesystemBrowserResponse;
import br.com.cezarcirqueira.mirror.app.model.dto.FilesystemEntry;
import br.com.cezarcirqueira.mirror.app.services.SystemService;
import br.com.cezarcirqueira.mirror.app.util.PathUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
public class SystemServiceImpl implements SystemService {

    @Override
    public FilesystemBrowserResponse listDirectories(String pathStr) {
        if (pathStr == null || pathStr.isBlank()) {
            return listRoots();
        }

        Path path = PathUtils.resolve(pathStr);
        String canonical = PathUtils.toCanonical(path);

        if (!Files.exists(path)) {
            throw new ResourceNotFoundException("Path not found: " + canonical);
        }
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("Path is not a directory: " + canonical);
        }

        List<FilesystemEntry> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path, this::isVisibleDirectory)) {
            for (Path child : stream) {
                entries.add(FilesystemEntry.builder()
                        .name(child.getFileName().toString())
                        .path(PathUtils.toCanonical(child))
                        .build());
            }
        } catch (AccessDeniedException ex) {
            throw new SecurityException("Access denied: " + canonical);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to list directory: " + ex.getMessage(), ex);
        }

        entries.sort(Comparator.comparing(FilesystemEntry::getName, String.CASE_INSENSITIVE_ORDER));

        Path parent = path.getParent();
        return FilesystemBrowserResponse.builder()
                .currentPath(canonical)
                .parentPath(parent == null ? null : PathUtils.toCanonical(parent))
                .entries(entries)
                .build();
    }

    private FilesystemBrowserResponse listRoots() {
        List<FilesystemEntry> entries = new ArrayList<>();
        for (File root : File.listRoots()) {
            if (!root.canRead()) {
                continue;
            }
            String absolutePath = PathUtils.toCanonical(root.toPath());
            String displayName = stripTrailingSeparator(absolutePath);
            entries.add(FilesystemEntry.builder()
                    .name(displayName.isBlank() ? absolutePath : displayName)
                    .path(absolutePath)
                    .build());
        }
        entries.sort(Comparator.comparing(FilesystemEntry::getName, String.CASE_INSENSITIVE_ORDER));
        return FilesystemBrowserResponse.builder()
                .currentPath(null)
                .parentPath(null)
                .entries(entries)
                .build();
    }

    private boolean isVisibleDirectory(Path candidate) {
        try {
            if (!Files.isDirectory(candidate)) {
                return false;
            }
            return !Files.isHidden(candidate);
        } catch (IOException ex) {
            log.debug("Skipping entry due to I/O error: {} ({})", candidate, ex.getMessage());
            return false;
        }
    }

    private String stripTrailingSeparator(String path) {
        if (path.length() > 1 && path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }
}
