package br.com.cezarcirqueira.mirror.app.adapter.out.peer;

import br.com.cezarcirqueira.mirror.app.application.config.MirrorProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PeerMirrorHttpClient {

    private final MirrorProperties mirrorProperties;
    private final ObjectMapper objectMapper;

    public PeerMirrorHttpClient(MirrorProperties mirrorProperties, ObjectMapper objectMapper) {
        this.mirrorProperties = mirrorProperties;
        this.objectMapper = objectMapper;
    }

    private HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(mirrorProperties.getPeerConnectTimeoutMs()))
                .build();
    }

    public List<ManifestEntryResponse> fetchManifest(String baseUrl, String mirrorGuid, String secret)
            throws IOException, InterruptedException {
        URI uri = URI.create(trimBase(baseUrl) + "/api/v1/mirrors/" + mirrorGuid + "/manifest");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(mirrorProperties.getPeerReadTimeoutMs()))
                .header("X-Mirror-Token", secret)
                .GET()
                .build();
        HttpResponse<String> response =
                httpClient().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Manifest HTTP " + response.statusCode() + ": " + response.body());
        }
        return objectMapper.readValue(response.body(), new TypeReference<>() {});
    }

    public byte[] downloadFile(String baseUrl, String mirrorGuid, String secret, String relativePath)
            throws IOException, InterruptedException {
        URI uri = fileUri(baseUrl, mirrorGuid, relativePath);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(mirrorProperties.getPeerReadTimeoutMs()))
                .header("X-Mirror-Token", secret)
                .GET()
                .build();
        HttpResponse<byte[]> response = httpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Download HTTP " + response.statusCode());
        }
        return response.body();
    }

    public void putFile(String baseUrl, String mirrorGuid, String secret, String relativePath, Path file)
            throws IOException, InterruptedException {
        URI uri = fileUri(baseUrl, mirrorGuid, relativePath);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(mirrorProperties.getPeerReadTimeoutMs()))
                .header("X-Mirror-Token", secret)
                .header("Content-Type", "application/octet-stream")
                .PUT(HttpRequest.BodyPublishers.ofFile(file))
                .build();
        HttpResponse<Void> response = httpClient().send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Upload HTTP " + response.statusCode());
        }
    }

    public void deleteFile(String baseUrl, String mirrorGuid, String secret, String relativePath)
            throws IOException, InterruptedException {
        URI uri = fileUri(baseUrl, mirrorGuid, relativePath);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(mirrorProperties.getPeerReadTimeoutMs()))
                .header("X-Mirror-Token", secret)
                .DELETE()
                .build();
        HttpResponse<Void> response = httpClient().send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() / 100 != 2 && response.statusCode() != 404) {
            throw new IOException("Delete HTTP " + response.statusCode());
        }
    }

    private URI fileUri(String baseUrl, String mirrorGuid, String relativePath) {
        String encoded = encodeRelativePath(relativePath);
        return URI.create(trimBase(baseUrl) + "/api/v1/mirrors/" + mirrorGuid + "/files/" + encoded);
    }

    private String encodeRelativePath(String relativePath) {
        String normalized = relativePath.replace('\\', '/');
        String[] parts = normalized.split("/");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append('/');
            }
            sb.append(URLEncoder.encode(parts[i], StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return sb.toString();
    }

    private String trimBase(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    public record ManifestEntryResponse(
            String relativePath, String sha256Hex, long sizeBytes, long lastModifiedEpochMs) {}
}
