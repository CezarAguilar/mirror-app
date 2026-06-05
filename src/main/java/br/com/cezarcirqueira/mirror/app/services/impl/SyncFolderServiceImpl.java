package br.com.cezarcirqueira.mirror.app.services.impl;

import br.com.cezarcirqueira.mirror.app.exceptions.ResourceNotFoundException;
import br.com.cezarcirqueira.mirror.app.model.SyncFolder;
import br.com.cezarcirqueira.mirror.app.model.dto.SyncFolderRequest;
import br.com.cezarcirqueira.mirror.app.model.dto.SyncFolderResponse;
import br.com.cezarcirqueira.mirror.app.model.dto.tree.DirectoryDTO;
import br.com.cezarcirqueira.mirror.app.model.dto.tree.FileDTO;
import br.com.cezarcirqueira.mirror.app.model.dto.tree.FileSystemNodeDTO;
import br.com.cezarcirqueira.mirror.app.repositories.SyncFolderRepository;
import br.com.cezarcirqueira.mirror.app.services.FolderWatcherService;
import br.com.cezarcirqueira.mirror.app.services.SyncFolderService;
import br.com.cezarcirqueira.mirror.app.util.FilesystemIgnoreFilter;
import br.com.cezarcirqueira.mirror.app.util.HashUtils;
import br.com.cezarcirqueira.mirror.app.util.PathUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyncFolderServiceImpl implements SyncFolderService {

    private final SyncFolderRepository repository;
    private final FolderWatcherService folderWatcherService;
    private final FilesystemIgnoreFilter ignoreFilter;

    @Override
    @Transactional
    public SyncFolderResponse create(SyncFolderRequest request) {
        String canonicalPath = normalizeAndValidateBasePath(request.getBasePath());
        SyncFolder entity = SyncFolder.builder()
                .guid(request.getGuid())
                .basePath(canonicalPath)
                .build();

        entity = repository.save(entity);
        folderWatcherService.registerFolder(entity);
        return mapToResponse(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public SyncFolderResponse findByGuid(UUID guid) {
        SyncFolder entity = repository.findByGuid(guid)
                .orElseThrow(() -> new ResourceNotFoundException("SyncFolder not found: " + guid));
        return mapToResponse(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SyncFolderResponse> findAll() {
        return repository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public SyncFolderResponse update(UUID guid, SyncFolderRequest request) {
        SyncFolder entity = repository.findByGuid(guid)
                .orElseThrow(() -> new ResourceNotFoundException("SyncFolder not found: " + guid));
        
        folderWatcherService.unregisterFolder(guid);
        
        if (request.getGuid() != null) {
            entity.setGuid(request.getGuid());
        }
        if (request.getBasePath() != null) {
            entity.setBasePath(normalizeAndValidateBasePath(request.getBasePath()));
        }

        entity = repository.save(entity);
        folderWatcherService.registerFolder(entity);
        return mapToResponse(entity);
    }

    @Override
    @Transactional
    public void delete(UUID guid) {
        if (!repository.existsByGuid(guid)) {
            throw new ResourceNotFoundException("SyncFolder not found: " + guid);
        }
        repository.deleteByGuid(guid);
        folderWatcherService.unregisterFolder(guid);
    }

    @Override
    @Transactional(readOnly = true)
    public DirectoryDTO listContent(UUID guid) {
        SyncFolder entity = repository.findByGuid(guid)
                .orElseThrow(() -> new ResourceNotFoundException("SyncFolder not found: " + guid));

        Path basePath = Paths.get(entity.getBasePath()).toAbsolutePath().normalize();
        if (!Files.exists(basePath) || !Files.isDirectory(basePath)) {
            throw new ResourceNotFoundException("basePath does not exist: " + entity.getBasePath());
        }

        return buildDirectoryNode(basePath, basePath);
    }

    private DirectoryDTO buildDirectoryNode(Path basePath, Path current) {
        List<FileSystemNodeDTO> children = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(current)) {
            List<Path> entries = new ArrayList<>();
            stream.forEach(entries::add);
            entries.sort(directoryFirstThenNameComparator());

            for (Path entry : entries) {
                if (ignoreFilter.shouldIgnore(entry)) {
                    continue;
                }
                if (Files.isDirectory(entry)) {
                    children.add(buildDirectoryNode(basePath, entry));
                } else if (Files.isRegularFile(entry)) {
                    children.add(buildFileNode(basePath, entry));
                }
            }
        } catch (IOException ex) {
            log.warn("Failed to read directory {}: {}", current, ex.getMessage());
        }

        return new DirectoryDTO(nodeName(basePath, current), relativePath(basePath, current), children);
    }

    private FileDTO buildFileNode(Path basePath, Path file) {
        String hash = HashUtils.sha256Quietly(file);
        if (hash == null) {
            log.warn("Failed to compute SHA-256 for file {}", file);
        }
        return new FileDTO(file.getFileName().toString(), relativePath(basePath, file), hash);
    }

    private static String nodeName(Path basePath, Path current) {
        if (current.equals(basePath)) {
            Path fileName = basePath.getFileName();
            return fileName == null ? basePath.toString() : fileName.toString();
        }
        return current.getFileName().toString();
    }

    private static String relativePath(Path basePath, Path current) {
        if (current.equals(basePath)) {
            return "";
        }
        return basePath.relativize(current).toString().replace('\\', '/');
    }

    private static Comparator<Path> directoryFirstThenNameComparator() {
        return Comparator
                .comparing((Path p) -> !Files.isDirectory(p))
                .thenComparing(p -> p.getFileName().toString().toLowerCase());
    }

    private SyncFolderResponse mapToResponse(SyncFolder entity) {
        return SyncFolderResponse.builder()
                .guid(entity.getGuid())
                .creationDate(entity.getCreationDate())
                .basePath(entity.getBasePath())
                .build();
    }

    private static String normalizeAndValidateBasePath(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("basePath is required");
        }
        Path resolved = PathUtils.resolve(input);
        String canonical = PathUtils.toCanonical(resolved);
        if (!Files.exists(resolved)) {
            throw new ResourceNotFoundException("basePath does not exist: " + canonical);
        }
        if (!Files.isDirectory(resolved)) {
            throw new IllegalArgumentException("basePath is not a directory: " + canonical);
        }
        return canonical;
    }
}
