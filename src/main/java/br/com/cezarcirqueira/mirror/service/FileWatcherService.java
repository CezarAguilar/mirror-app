package br.com.cezarcirqueira.mirror.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class FileWatcherService {

    private static final Logger log = LoggerFactory.getLogger(FileWatcherService.class);

    private final FileSyncService fileSyncService;

    private WatchService watchService;
    private final Map<WatchKey, WatchedFolder> keyMap = new ConcurrentHashMap<>();
    private final Map<String, WatchKey> guidToKey = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> suppressedUntil = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "file-watcher");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Future<?> watchTask;

    public FileWatcherService(@Lazy FileSyncService fileSyncService) {
        this.fileSyncService = fileSyncService;
    }

    public void startWatching(String guid, String folderPath) {
        Path path = Paths.get(folderPath);
        if (!Files.exists(path)) {
            try { Files.createDirectories(path); } catch (IOException e) {
                log.warn("Cannot create watched directory: {}", folderPath);
                return;
            }
        }

        try {
            if (watchService == null) {
                watchService = FileSystems.getDefault().newWatchService();
            }
            if (guidToKey.containsKey(guid)) {
                guidToKey.get(guid).cancel();
                guidToKey.remove(guid);
            }

            WatchKey key = path.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);

            keyMap.put(key, new WatchedFolder(guid, path));
            guidToKey.put(guid, key);
            log.info("Watching folder guid={} path={}", guid, folderPath);

            if (!running.get()) {
                startLoop();
            }
        } catch (IOException e) {
            log.error("Failed to start watching {}: {}", folderPath, e.getMessage());
        }
    }

    public void suppressFor(String guid, String relativePath) {
        suppressedUntil.put(guid + ":" + relativePath, System.currentTimeMillis() + 1000);
    }

    private boolean isSuppressed(String guid, String relativePath) {
        Long expiry = suppressedUntil.get(guid + ":" + relativePath);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            suppressedUntil.remove(guid + ":" + relativePath);
            return false;
        }
        return true;
    }

    public void stopWatching(String guid) {
        WatchKey key = guidToKey.remove(guid);
        if (key != null) {
            keyMap.remove(key);
            key.cancel();
            log.info("Stopped watching guid={}", guid);
        }
    }

    private void startLoop() {
        running.set(true);
        watchTask = executor.submit(() -> {
            log.info("File watcher loop started");
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                WatchKey key;
                try {
                    key = watchService.take();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (ClosedWatchServiceException e) {
                    break;
                }

                WatchedFolder watched = keyMap.get(key);
                if (watched != null) {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();
                        if (kind == StandardWatchEventKinds.OVERFLOW) continue;

                        @SuppressWarnings("unchecked")
                        WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                        Path changed = watched.basePath().resolve(pathEvent.context());
                        if (Files.isDirectory(changed)) continue;

                        String relative = watched.basePath().relativize(changed).toString().replace('\\', '/');
                        String action = mapAction(kind);

                        if (isSuppressed(watched.guid(), relative)) {
                            log.debug("Suppressing sync-originated event: {} {} in guid={}", action, relative, watched.guid());
                            continue;
                        }

                        log.info("File event: {} {} in guid={}", action, relative, watched.guid());
                        fileSyncService.handleLocalChange(watched.guid(), relative, action, watched.basePath().toString());
                    }
                }
                key.reset();
            }
            log.info("File watcher loop stopped");
            running.set(false);
        });
    }

    private String mapAction(WatchEvent.Kind<?> kind) {
        if (kind == StandardWatchEventKinds.ENTRY_CREATE) return "CREATE";
        if (kind == StandardWatchEventKinds.ENTRY_MODIFY) return "MODIFY";
        if (kind == StandardWatchEventKinds.ENTRY_DELETE) return "DELETE";
        return "UNKNOWN";
    }

    private record WatchedFolder(String guid, Path basePath) {}
}
