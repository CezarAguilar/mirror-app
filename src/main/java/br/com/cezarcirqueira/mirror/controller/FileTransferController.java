package br.com.cezarcirqueira.mirror.controller;

import br.com.cezarcirqueira.mirror.service.AppStateService;
import br.com.cezarcirqueira.mirror.service.FolderRegistryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class FileTransferController {

    private static final Logger log = LoggerFactory.getLogger(FileTransferController.class);

    private final AppStateService appStateService;
    private final FolderRegistryService folderRegistryService;

    public FileTransferController(AppStateService appStateService,
                                   FolderRegistryService folderRegistryService) {
        this.appStateService = appStateService;
        this.folderRegistryService = folderRegistryService;
    }

    @PostMapping("/files/upload")
    public ResponseEntity<Void> upload(
            @RequestParam String guid,
            @RequestParam String relativePath,
            @RequestParam MultipartFile file) {

        Optional<String> folderPath = resolveFolderPath(guid);
        if (folderPath.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        try {
            Path target = Paths.get(folderPath.get(),
                    relativePath.replace("/", FileSystems.getDefault().getSeparator()));
            Files.createDirectories(target.getParent());
            file.transferTo(target);
            log.info("File uploaded: {} in guid={}", relativePath, guid);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Upload failed for {}: {}", relativePath, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/files/download")
    public ResponseEntity<InputStreamResource> download(
            @RequestParam String guid,
            @RequestParam String path) {

        Optional<String> folderPath = resolveFolderPath(guid);
        if (folderPath.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        try {
            Path file = Paths.get(folderPath.get(),
                    path.replace("/", FileSystems.getDefault().getSeparator()));

            if (!Files.exists(file) || Files.isDirectory(file)) {
                return ResponseEntity.notFound().build();
            }

            InputStream is = Files.newInputStream(file);
            InputStreamResource resource = new InputStreamResource(is);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFileName() + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(Files.size(file))
                    .body(resource);
        } catch (Exception e) {
            log.error("Download failed for {}: {}", path, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/files/delete")
    public ResponseEntity<Void> delete(
            @RequestParam String guid,
            @RequestParam String path) {

        Optional<String> folderPath = resolveFolderPath(guid);
        if (folderPath.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        try {
            Path file = Paths.get(folderPath.get(),
                    path.replace("/", FileSystems.getDefault().getSeparator()));
            Files.deleteIfExists(file);
            log.info("File deleted: {} in guid={}", path, guid);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Delete failed for {}: {}", path, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/sync/state")
    public ResponseEntity<List<Map<String, String>>> syncState(@RequestParam String guid) {
        Optional<String> folderPath = resolveFolderPath(guid);
        if (folderPath.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        try {
            Path root = Paths.get(folderPath.get());
            if (!Files.exists(root)) {
                return ResponseEntity.ok(List.of());
            }

            List<Map<String, String>> entries = new ArrayList<>();
            Files.walk(root)
                    .filter(p -> !Files.isDirectory(p))
                    .forEach(p -> {
                        String relative = root.relativize(p).toString().replace('\\', '/');
                        String hash = computeHash(p);
                        entries.add(Map.of("relativePath", relative, "hash", hash));
                    });

            return ResponseEntity.ok(entries);
        } catch (Exception e) {
            log.error("State listing failed for guid={}: {}", guid, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    private Optional<String> resolveFolderPath(String guid) {
        if (appStateService.isMaster()) {
            return folderRegistryService.findMasterFolderByGuid(guid)
                    .map(f -> f.getFolderPath());
        } else if (appStateService.isSlave()) {
            return folderRegistryService.findMappingByGuid(guid)
                    .filter(m -> m.getLocalPath() != null)
                    .map(m -> m.getLocalPath());
        }
        return Optional.empty();
    }

    private String computeHash(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream is = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
