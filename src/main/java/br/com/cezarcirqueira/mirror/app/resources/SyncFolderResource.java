package br.com.cezarcirqueira.mirror.app.resources;

import br.com.cezarcirqueira.mirror.app.exceptions.ReplayAttemptException;
import br.com.cezarcirqueira.mirror.app.exceptions.ResourceNotFoundException;
import br.com.cezarcirqueira.mirror.app.model.dto.EncryptedTargetPayload;
import br.com.cezarcirqueira.mirror.app.model.dto.SyncFolderRequest;
import br.com.cezarcirqueira.mirror.app.model.dto.SyncFolderResponse;
import br.com.cezarcirqueira.mirror.app.model.dto.tree.DirectoryDTO;
import br.com.cezarcirqueira.mirror.app.services.AuditService;
import br.com.cezarcirqueira.mirror.app.services.CryptoService;
import br.com.cezarcirqueira.mirror.app.services.ReplayProtectionService;
import br.com.cezarcirqueira.mirror.app.services.SyncFolderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.SecretKey;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/sync-folders")
@RequiredArgsConstructor
public class SyncFolderResource {

    private final SyncFolderService service;
    private final CryptoService cryptoService;
    private final ReplayProtectionService replayProtectionService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity<SyncFolderResponse> create(@RequestBody SyncFolderRequest request) {
        SyncFolderResponse response = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{guid}")
    public ResponseEntity<SyncFolderResponse> findByGuid(@PathVariable UUID guid) {
        SyncFolderResponse response = service.findByGuid(guid);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<SyncFolderResponse>> findAll() {
        List<SyncFolderResponse> response = service.findAll();
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{guid}")
    public ResponseEntity<SyncFolderResponse> update(@PathVariable UUID guid, @RequestBody SyncFolderRequest request) {
        SyncFolderResponse response = service.update(guid, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{guid}")
    public ResponseEntity<Void> delete(@PathVariable UUID guid) {
        service.delete(guid);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{guid}/content")
    public ResponseEntity<DirectoryDTO> listContent(@PathVariable UUID guid) {
        DirectoryDTO content = service.listContent(guid);
        return ResponseEntity.ok(content);
    }

    @GetMapping("/{guid}/download")
    public void download(@PathVariable UUID guid,
                         @RequestHeader("X-Encrypted-Session-Key") String encryptedSessionKey,
                         @RequestHeader("X-Target-Encrypted") String encryptedTargetPath,
                         HttpServletRequest request,
                         HttpServletResponse response) throws IOException {

        String clientIp = resolveClientIp(request);

        try {
            SyncFolderResponse folder = service.findByGuid(guid);

            SecretKey sessionKey = cryptoService.unwrapSessionKey(encryptedSessionKey);
            EncryptedTargetPayload target = cryptoService.decryptToObject(
                    encryptedTargetPath, sessionKey, EncryptedTargetPayload.class);

            replayProtectionService.validateAndConsume(target.getNonce(), target.getTimestamp());

            Path baseDir = Paths.get(folder.getBasePath()).toAbsolutePath().normalize();
            String relative = target.getPath() == null ? "" : target.getPath().replaceFirst("^[/\\\\]+", "");
            Path requested = baseDir.resolve(relative).toAbsolutePath().normalize();
            if (!requested.startsWith(baseDir)) {
                throw new SecurityException("Access denied: requested path escapes the sync folder boundary");
            }
            if (!Files.isRegularFile(requested)) {
                throw new FileNotFoundException("File not found: " + target.getPath());
            }

            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");

            try (InputStream fileStream = Files.newInputStream(requested, StandardOpenOption.READ);
                 OutputStream responseStream = response.getOutputStream()) {
                cryptoService.encryptStream(fileStream, responseStream, sessionKey);
            }

            auditService.downloadSuccess(guid, clientIp, requested);
        } catch (RuntimeException | IOException ex) {
            auditService.downloadDenied(guid, clientIp, classifyFailure(ex));
            throw ex;
        }
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private static String classifyFailure(Exception ex) {
        if (ex instanceof ReplayAttemptException) return "replay_attempt";
        if (ex instanceof ResourceNotFoundException) return "sync_folder_not_found";
        if (ex instanceof FileNotFoundException) return "file_not_found";
        if (ex instanceof SecurityException) return "path_traversal_or_security";
        if (ex instanceof IllegalArgumentException) return "invalid_input";
        return "internal_error";
    }

    // ------------------------------------------------------------------
    // TEMPORARY: helper endpoint that simulates the encrypted-client side
    // entirely on the server, so the real /download endpoint can be exercised
    // with a plain `curl -G --data-urlencode "path=..."`. Remove once a proper
    // client exists.
    // ------------------------------------------------------------------
    @GetMapping("/{guid}/download-test")
    public void downloadTest(@PathVariable UUID guid,
                             @RequestParam("path") String relativePath,
                             HttpServletRequest request,
                             HttpServletResponse response) throws IOException {

        SecretKey sessionKey = cryptoService.generateSessionKey();
        String encryptedSessionKey = cryptoService.wrapSessionKey(sessionKey);

        EncryptedTargetPayload targetPayload = EncryptedTargetPayload.builder()
                .path(relativePath)
                .nonce(UUID.randomUUID().toString())
                .timestamp(Instant.now())
                .build();
        String targetJson = objectMapper.writeValueAsString(targetPayload);
        String encryptedTarget = cryptoService.encryptToBase64(targetJson, sessionKey);

        URI uri = URI.create(String.format("%s://127.0.0.1:%d/sync-folders/%s/download",
                request.getScheme(), request.getServerPort(), guid));

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(uri)
                .header("X-Encrypted-Session-Key", encryptedSessionKey)
                .header("X-Target-Encrypted", encryptedTarget)
                .header("X-Forwarded-For", request.getRemoteAddr())
                .GET()
                .build();

        HttpResponse<InputStream> upstream;
        try {
            upstream = HttpClient.newHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("download-test self-call interrupted", e);
        }

        if (upstream.statusCode() >= 400) {
            response.setStatus(upstream.statusCode());
            response.setContentType(upstream.headers()
                    .firstValue(HttpHeaders.CONTENT_TYPE)
                    .orElse(MediaType.APPLICATION_JSON_VALUE));
            try (InputStream errBody = upstream.body();
                 OutputStream out = response.getOutputStream()) {
                errBody.transferTo(out);
            }
            return;
        }

        String filename = extractFilename(relativePath);
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");

        try (InputStream cipherStream = upstream.body();
             OutputStream out = response.getOutputStream()) {
            cryptoService.decryptStream(cipherStream, out, sessionKey);
        }
    }

    private static String extractFilename(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return "download.bin";
        }
        String trimmed = relativePath.replace('\\', '/');
        int idx = trimmed.lastIndexOf('/');
        String tail = idx >= 0 ? trimmed.substring(idx + 1) : trimmed;
        return tail.isBlank() ? "download.bin" : tail;
    }
}
