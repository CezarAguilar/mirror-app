package br.com.cezarcirqueira.mirror.app.adapter.in.filesystem;

import br.com.cezarcirqueira.mirror.app.application.config.MirrorProperties;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.LocalReplicaRepository;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity.LocalReplicaEntity;
import br.com.cezarcirqueira.mirror.app.application.sync.FileIndexService;
import br.com.cezarcirqueira.mirror.app.application.sync.OutboundSyncService;
import br.com.cezarcirqueira.mirror.app.application.sync.RecentRemoteWriteRegistry;
import br.com.cezarcirqueira.mirror.app.application.sync.RescanDelta;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class RecursiveDirectoryWatchCoordinator {

    private static final Logger log = LoggerFactory.getLogger(RecursiveDirectoryWatchCoordinator.class);

    private final LocalReplicaRepository localReplicaRepository;
    private final FileIndexService fileIndexService;
    private final OutboundSyncService outboundSyncService;
    private final RecentRemoteWriteRegistry recentRemoteWriteRegistry;
    private final MirrorProperties mirrorProperties;

    private final ExecutorService watchExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "mirror-watch");
        t.setDaemon(true);
        return t;
    });

    private final ScheduledExecutorService debounceScheduler = Executors.newScheduledThreadPool(1, r -> {
        Thread t = new Thread(r, "mirror-debounce");
        t.setDaemon(true);
        return t;
    });

    private final Map<String, WatchSession> sessionsByMirror = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> debounceByMirror = new ConcurrentHashMap<>();

    public RecursiveDirectoryWatchCoordinator(
            LocalReplicaRepository localReplicaRepository,
            FileIndexService fileIndexService,
            OutboundSyncService outboundSyncService,
            RecentRemoteWriteRegistry recentRemoteWriteRegistry,
            MirrorProperties mirrorProperties) {
        this.localReplicaRepository = localReplicaRepository;
        this.fileIndexService = fileIndexService;
        this.outboundSyncService = outboundSyncService;
        this.recentRemoteWriteRegistry = recentRemoteWriteRegistry;
        this.mirrorProperties = mirrorProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startAll() {
        List<LocalReplicaEntity> replicas = localReplicaRepository.findAll();
        for (LocalReplicaEntity replica : replicas) {
            try {
                startForMirror(replica.getMirrorGuid(), Path.of(replica.getRootPath()));
            } catch (IOException e) {
                log.error("Failed to start watch for mirror {}", replica.getMirrorGuid(), e);
            }
        }
    }

    public synchronized void restartForMirror(String mirrorGuid) throws IOException {
        stopForMirror(mirrorGuid);
        LocalReplicaEntity replica =
                localReplicaRepository.findByMirrorGuid(mirrorGuid).orElse(null);
        if (replica != null) {
            startForMirror(mirrorGuid, Path.of(replica.getRootPath()));
        }
    }

    public synchronized void stopForMirror(String mirrorGuid) {
        WatchSession session = sessionsByMirror.remove(mirrorGuid);
        if (session != null) {
            session.close();
        }
        ScheduledFuture<?> f = debounceByMirror.remove(mirrorGuid);
        if (f != null) {
            f.cancel(false);
        }
    }

    private void startForMirror(String mirrorGuid, Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            log.warn("Skip watch; not a directory: {}", root);
            return;
        }
        WatchService watchService = FileSystems.getDefault().newWatchService();
        Map<WatchKey, Path> keys = new ConcurrentHashMap<>();
        registerAllDirectories(root, watchService, keys);
        WatchSession session = new WatchSession(mirrorGuid, root, watchService, keys);
        sessionsByMirror.put(mirrorGuid, session);
        watchExecutor.submit(() -> watchLoop(session));
        log.info("Started recursive watch for mirror {} at {}", mirrorGuid, root);
    }

    private void registerAllDirectories(Path start, WatchService ws, Map<WatchKey, Path> keys) throws IOException {
        Files.walkFileTree(
                start,
                new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        WatchKey key =
                                dir.register(ws, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
                        keys.put(key, dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
    }

    private void watchLoop(WatchSession session) {
        while (!session.closed) {
            WatchKey key;
            try {
                key = session.watchService.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            Path dir = session.keys.get(key);
            if (dir == null) {
                if (!session.closed) {
                    key.reset();
                }
                continue;
            }
            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    scheduleDebouncedRescan(session);
                    continue;
                }
                Path name = (Path) event.context();
                Path child = dir.resolve(name);
                if (Files.isDirectory(child)) {
                    if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                        try {
                            registerAllDirectories(child, session.watchService, session.keys);
                        } catch (IOException e) {
                            log.warn("Failed to register subtree {}", child, e);
                        }
                    }
                }
                scheduleDebouncedRescan(session);
            }
            boolean valid = key.reset();
            if (!valid) {
                session.keys.remove(key);
            }
        }
    }

    private void scheduleDebouncedRescan(WatchSession session) {
        debounceByMirror.compute(session.mirrorGuid, (guid, existing) -> {
            if (existing != null) {
                existing.cancel(false);
            }
            return debounceScheduler.schedule(
                    () -> {
                        try {
                            RescanDelta delta = fileIndexService.fullRescanMirrorReturningDelta(session.mirrorGuid);
                            outboundSyncService.pushDeltaAfterRescan(
                                    session.mirrorGuid, delta, recentRemoteWriteRegistry);
                        } catch (Exception e) {
                            log.warn("Debounced rescan failed for mirror {}", session.mirrorGuid, e);
                        }
                    },
                    mirrorProperties.getWatchDebounceMs(),
                    TimeUnit.MILLISECONDS);
        });
    }

    @PreDestroy
    public void shutdown() {
        sessionsByMirror.values().forEach(WatchSession::close);
        sessionsByMirror.clear();
        watchExecutor.shutdownNow();
        debounceScheduler.shutdownNow();
    }

    private static final class WatchSession {
        private final String mirrorGuid;
        private final Path root;
        private final WatchService watchService;
        private final Map<WatchKey, Path> keys;
        private volatile boolean closed;

        private WatchSession(String mirrorGuid, Path root, WatchService watchService, Map<WatchKey, Path> keys) {
            this.mirrorGuid = mirrorGuid;
            this.root = root;
            this.watchService = watchService;
            this.keys = keys;
        }

        private void close() {
            closed = true;
            try {
                watchService.close();
            } catch (IOException ignored) {
                // ignore
            }
        }
    }
}
