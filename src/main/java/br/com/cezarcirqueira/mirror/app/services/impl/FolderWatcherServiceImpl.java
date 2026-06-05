package br.com.cezarcirqueira.mirror.app.services.impl;

import br.com.cezarcirqueira.mirror.app.model.SyncFolder;
import br.com.cezarcirqueira.mirror.app.model.dto.sync.FileSyncEventType;
import br.com.cezarcirqueira.mirror.app.model.dto.sync.FileSyncMessage;
import br.com.cezarcirqueira.mirror.app.repositories.SyncFolderRepository;
import br.com.cezarcirqueira.mirror.app.services.FolderWatcherService;
import br.com.cezarcirqueira.mirror.app.services.WebSocketService;
import br.com.cezarcirqueira.mirror.app.util.HashUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FolderWatcherServiceImpl implements FolderWatcherService {

    private static final String FILE_SYNC_QUEUE = "fileSync";

    private final SyncFolderRepository repository;
    private final WebSocketService webSocketService;
    private final Map<UUID, WatchService> watchServices = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    private WatchEvent.Modifier fileTreeModifier;

    @PostConstruct
    public void init() {
        try {
            Class<?> modifierClass = Class.forName("com.sun.nio.file.ExtendedWatchEventModifier");
            fileTreeModifier = (WatchEvent.Modifier) modifierClass.getField("FILE_TREE").get(null);
            log.info("FILE_TREE modifier encontrado. Diretórios internos não sofrerão lock do sistema operacional.");
        } catch (Exception e) {
            log.info("FILE_TREE modifier indisponível. Usando fallback de registro explicito (pode causar lock no Windows).");
        }

        List<SyncFolder> folders = repository.findAll();
        for (SyncFolder folder : folders) {
            registerFolder(folder);
        }
    }

    @PreDestroy
    public void destroy() {
        watchServices.values().forEach(ws -> {
            try {
                ws.close();
            } catch (IOException e) {
                log.error("Failed to close watch service", e);
            }
        });
        executorService.shutdownNow();
    }

    @Override
    public void registerFolder(SyncFolder folder) {
        Path basePath = Paths.get(folder.getBasePath());
        if (!Files.exists(basePath) || !Files.isDirectory(basePath)) {
            log.warn("Directory does not exist or is not a directory: {}", folder.getBasePath());
            return;
        }

        try {
            WatchService watchService = FileSystems.getDefault().newWatchService();
            Map<WatchKey, Path> keys = new HashMap<>();
            
            registerDirectory(basePath, watchService, keys);
            
            watchServices.put(folder.getGuid(), watchService);
            startWatching(folder.getGuid(), watchService, keys, basePath);
            
            log.info("Started watching folder: {}", folder.getBasePath());
        } catch (IOException e) {
            log.error("Failed to register watch service for folder: {}", folder.getBasePath(), e);
        }
    }

    @Override
    public void unregisterFolder(UUID guid) {
        WatchService ws = watchServices.remove(guid);
        if (ws != null) {
            try {
                ws.close();
                log.info("Stopped watching folder with GUID: {}", guid);
            } catch (IOException e) {
                log.error("Failed to close watch service for GUID: {}", guid, e);
            }
        }
    }

    private void registerDirectory(Path start, WatchService watchService, Map<WatchKey, Path> keys) throws IOException {
        WatchEvent.Kind<?>[] events = {
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY
        };

        if (fileTreeModifier != null) {
            WatchKey key = start.register(watchService, events, fileTreeModifier);
            keys.put(key, start);
        } else {
            Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    WatchKey key = dir.register(watchService, events);
                    keys.put(key, dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    private void startWatching(UUID guid, WatchService watchService, Map<WatchKey, Path> keys, Path basePath) {
        executorService.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                WatchKey key;
                try {
                    key = watchService.take();
                } catch (InterruptedException x) {
                    Thread.currentThread().interrupt();
                    return;
                }

                Path dir = keys.get(key);
                if (dir == null) {
                    continue;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }

                    WatchEvent<Path> ev = cast(event);
                    Path name = ev.context();
                    Path child = dir.resolve(name);

                    try {
                        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                            if (Files.isDirectory(child) && fileTreeModifier == null) {
                                registerDirectory(child, watchService, keys);
                            }
                            processEvent(guid, basePath, child, FileSyncEventType.CREATED);
                        } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                            processEvent(guid, basePath, child, FileSyncEventType.MODIFIED);
                        } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                            processEvent(guid, basePath, child, FileSyncEventType.DELETED);
                        }
                    } catch (IOException e) {
                        log.error("Error processing event", e);
                    }
                }

                boolean valid = key.reset();
                if (!valid) {
                    keys.remove(key);
                    if (keys.isEmpty()) {
                        break;
                    }
                }
            }
        });
    }

    private void processEvent(UUID guid, Path basePath, Path path, FileSyncEventType eventType) {
        String relativePath = basePath.relativize(path).toString().replace("\\", "/");

        if (eventType != FileSyncEventType.DELETED && Files.isDirectory(path)) {
            log.debug("sync-event guid={} path={} type={} skipped=directory", guid, relativePath, eventType);
            return;
        }

        String hash = null;
        if (eventType != FileSyncEventType.DELETED) {
            if (!Files.isRegularFile(path)) {
                log.debug("sync-event guid={} path={} type={} skipped=not-regular-file", guid, relativePath, eventType);
                return;
            }
            hash = computeHashWithRetry(path);
            if (hash == null) {
                log.warn("sync-event guid={} path={} type={} skipped=hash-unavailable", guid, relativePath, eventType);
                return;
            }
        }

        log.info("sync-event guid={} path={} hash={} type={}", guid, relativePath, hash, eventType);

        if (!webSocketService.isRunning()) {
            log.debug("sync-event guid={} path={} type={} skipped=websocket-stopped", guid, relativePath, eventType);
            return;
        }

        FileSyncMessage message = FileSyncMessage.builder()
                .folderGuid(guid)
                .path(relativePath)
                .hash(hash)
                .eventType(eventType)
                .build();

        try {
            webSocketService.publish(FILE_SYNC_QUEUE, null, message);
        } catch (RuntimeException ex) {
            log.warn("Failed to publish sync-event guid={} path={} type={}: {}",
                    guid, relativePath, eventType, ex.getMessage());
        }
    }

    private String computeHashWithRetry(Path path) {
        int maxRetries = 5;
        for (int i = 0; i < maxRetries; i++) {
            try {
                return HashUtils.sha256(path);
            } catch (IOException e) {
                if (i == maxRetries - 1) {
                    log.warn("Failed to calculate SHA-256 for file after retries (file might be locked): {}", path);
                    return null;
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            } catch (NoSuchAlgorithmException e) {
                log.error("SHA-256 algorithm not found", e);
                return null;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>)event;
    }
}
