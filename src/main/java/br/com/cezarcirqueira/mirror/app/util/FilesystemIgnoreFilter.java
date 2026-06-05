package br.com.cezarcirqueira.mirror.app.util;

import br.com.cezarcirqueira.mirror.app.config.SyncFolderProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

@Component
public class FilesystemIgnoreFilter {

    private static final String EXTENSION_PREFIX = "*.";

    private final Set<String> directoryNames;
    private final Set<String> fileNamesWithoutExtension;
    private final Set<String> fileFullNames;
    private final Set<String> fileExtensions;

    public FilesystemIgnoreFilter(SyncFolderProperties properties) {
        Set<String> dirs = new HashSet<>();
        Set<String> filesNoExt = new HashSet<>();
        Set<String> fileFull = new HashSet<>();
        Set<String> exts = new HashSet<>();

        for (String raw : properties.getIgnore()) {
            if (raw == null) {
                continue;
            }
            String pattern = raw.trim();
            if (pattern.isEmpty()) {
                continue;
            }

            if (pattern.startsWith(EXTENSION_PREFIX)) {
                String ext = pattern.substring(EXTENSION_PREFIX.length());
                if (!ext.isEmpty()) {
                    exts.add(ext);
                }
            } else if (pattern.startsWith(".")) {
                dirs.add(pattern);
            } else if (pattern.contains(".")) {
                fileFull.add(pattern);
            } else {
                dirs.add(pattern);
                filesNoExt.add(pattern);
            }
        }

        this.directoryNames = Set.copyOf(dirs);
        this.fileNamesWithoutExtension = Set.copyOf(filesNoExt);
        this.fileFullNames = Set.copyOf(fileFull);
        this.fileExtensions = Set.copyOf(exts);
    }

    public boolean shouldIgnore(Path entry) {
        if (entry == null || entry.getFileName() == null) {
            return false;
        }
        return shouldIgnore(entry.getFileName().toString(), Files.isDirectory(entry));
    }

    public boolean shouldIgnore(String name, boolean directory) {
        if (name == null || name.isEmpty()) {
            return false;
        }

        if (directory) {
            return directoryNames.contains(name);
        }

        if (fileFullNames.contains(name)) {
            return true;
        }

        int dotIndex = name.lastIndexOf('.');
        boolean hasExtension = dotIndex > 0 && dotIndex < name.length() - 1;
        if (!hasExtension) {
            return fileNamesWithoutExtension.contains(name);
        }

        String extension = name.substring(dotIndex + 1);
        return fileExtensions.contains(extension);
    }
}
