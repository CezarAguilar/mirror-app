package br.com.cezarcirqueira.mirror.app.sync;

import br.com.cezarcirqueira.mirror.app.model.dto.EncryptedTargetPayload;
import br.com.cezarcirqueira.mirror.app.services.CryptoService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PeerDownloadClient {

    private static final int MAX_ERROR_BODY_BYTES = 512;

    private final CryptoService cryptoService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public void downloadTo(String peerBaseUrl, UUID folderGuid, String relativePath, Path targetFile) throws IOException {
        if (peerBaseUrl == null || peerBaseUrl.isBlank()) {
            throw new IllegalArgumentException("peerBaseUrl is required");
        }
        if (folderGuid == null) {
            throw new IllegalArgumentException("folderGuid is required");
        }
        if (targetFile == null) {
            throw new IllegalArgumentException("targetFile is required");
        }

        String normalizedBase = peerBaseUrl.endsWith("/") ? peerBaseUrl.substring(0, peerBaseUrl.length() - 1) : peerBaseUrl;
        String peerPem = fetchPeerPublicKey(normalizedBase);

        SecretKey sessionKey = cryptoService.generateSessionKey();
        String encryptedSessionKey = cryptoService.wrapSessionKey(sessionKey, peerPem);
        String encryptedTarget = buildEncryptedTargetHeader(sessionKey, relativePath);

        URI downloadUri = URI.create(normalizedBase + "/sync-folders/" + folderGuid + "/download");
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(downloadUri)
                .header("X-Encrypted-Session-Key", encryptedSessionKey)
                .header("X-Target-Encrypted", encryptedTarget)
                .GET()
                .build();

        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Peer download interrupted: " + downloadUri, e);
        }

        if (response.statusCode() >= 400) {
            String body = readErrorBody(response);
            throw new IOException("Peer download failed (" + response.statusCode() + ") "
                    + downloadUri + ": " + body);
        }

        Path parent = targetFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (InputStream cipherStream = response.body();
             OutputStream out = Files.newOutputStream(targetFile)) {
            cryptoService.decryptStream(cipherStream, out, sessionKey);
        } catch (IOException | RuntimeException ex) {
            try {
                Files.deleteIfExists(targetFile);
            } catch (IOException cleanupEx) {
                log.warn("Failed to delete partial download {}: {}", targetFile, cleanupEx.getMessage());
            }
            throw ex;
        }
    }

    private String fetchPeerPublicKey(String normalizedBase) throws IOException {
        URI uri = URI.create(normalizedBase + "/api/crypto/public-key");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Accept", "text/plain")
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Peer public-key fetch interrupted: " + uri, e);
        }
        if (response.statusCode() >= 400) {
            throw new IOException("Peer public-key fetch failed (" + response.statusCode() + ") "
                    + uri + ": " + truncate(response.body()));
        }
        String body = response.body();
        if (body == null || body.isBlank()) {
            throw new IOException("Peer public-key response is empty: " + uri);
        }
        return body;
    }

    private String buildEncryptedTargetHeader(SecretKey sessionKey, String relativePath) throws IOException {
        EncryptedTargetPayload targetPayload = EncryptedTargetPayload.builder()
                .path(relativePath)
                .nonce(UUID.randomUUID().toString())
                .timestamp(Instant.now())
                .build();
        String targetJson;
        try {
            targetJson = objectMapper.writeValueAsString(targetPayload);
        } catch (JsonProcessingException e) {
            throw new IOException("Failed to serialise encrypted target payload", e);
        }
        return cryptoService.encryptToBase64(targetJson, sessionKey);
    }

    private static String readErrorBody(HttpResponse<InputStream> response) {
        try (InputStream body = response.body()) {
            byte[] buffer = body.readNBytes(MAX_ERROR_BODY_BYTES);
            return new String(buffer, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "<unreadable error body: " + ex.getMessage() + ">";
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return "<null>";
        }
        return value.length() <= MAX_ERROR_BODY_BYTES ? value : value.substring(0, MAX_ERROR_BODY_BYTES);
    }
}
