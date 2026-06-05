package br.com.cezarcirqueira.mirror.app.services.impl;

import br.com.cezarcirqueira.mirror.app.model.SyncFolder;
import br.com.cezarcirqueira.mirror.app.repositories.SyncFolderRepository;
import br.com.cezarcirqueira.mirror.app.services.FolderWatcherService;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    private final SyncFolderRepository repository;
    private final Map<UUID, WatchService> watchServices = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

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
                            if (Files.isDirectory(child)) {
                                if (fileTreeModifier == null) {
                                    registerDirectory(child, watchService, keys);
                                }
                                processEvent(guid, basePath, child, "CREATED");
                            } else {
                                processEvent(guid, basePath, child, "CREATED");
                            }
                        } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                            processEvent(guid, basePath, child, "MODIFIED");
                        } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                            processEvent(guid, basePath, child, "DELETED");
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

    private void processEvent(UUID guid, Path basePath, Path path, String eventType) {
        String relativePath = basePath.relativize(path).toString().replace("\\", "/");
        String sha256 = "N/A";
        
        if (!"DELETED".equals(eventType) && Files.exists(path) && !Files.isDirectory(path)) {
            int maxRetries = 5;
            for (int i = 0; i < maxRetries; i++) {
                try {
                    sha256 = HashUtils.sha256(path);
                    break;
                } catch (IOException e) {
                    if (i == maxRetries - 1) {
                         log.warn("Failed to calculate SHA-256 for file after retries (file might be locked): {}", path);
                    } else {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                } catch (NoSuchAlgorithmException e) {
                    log.error("SHA-256 algorithm not found", e);
                    break;
                }
            }
        } else if (Files.isDirectory(path)) {
            sha256 = "DIRECTORY";
        }
        
        String timestamp = LocalDateTime.now().format(formatter);
        System.out.printf("%s %s %s %s [%s]%n", guid, timestamp, relativePath, sha256, eventType);
    }

    @SuppressWarnings("unchecked")
    static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>)event;
    }
}
