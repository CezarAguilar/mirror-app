package br.com.cezarcirqueira.mirror.app.adapter.in.web;

import br.com.cezarcirqueira.mirror.app.adapter.in.web.dto.ManifestEntryDto;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.MirrorDefinitionRepository;
import br.com.cezarcirqueira.mirror.app.application.sync.FileIndexService;
import br.com.cezarcirqueira.mirror.app.application.sync.InboundMirrorService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

@RestController
@RequestMapping("/api/v1/mirrors")
public class MirrorPeerRestController {

    private final MirrorDefinitionRepository mirrorDefinitionRepository;
    private final FileIndexService fileIndexService;
    private final InboundMirrorService inboundMirrorService;

    public MirrorPeerRestController(
            MirrorDefinitionRepository mirrorDefinitionRepository,
            FileIndexService fileIndexService,
            InboundMirrorService inboundMirrorService) {
        this.mirrorDefinitionRepository = mirrorDefinitionRepository;
        this.fileIndexService = fileIndexService;
        this.inboundMirrorService = inboundMirrorService;
    }

    public record MirrorHealthResponse(String status) {}

    @GetMapping("/{guid}/health")
    public MirrorHealthResponse health(@PathVariable String guid) {
        boolean exists = mirrorDefinitionRepository.existsById(guid);
        return new MirrorHealthResponse(exists ? "UP" : "UNKNOWN");
    }

    @GetMapping("/{guid}/manifest")
    public List<ManifestEntryDto> manifest(@PathVariable String guid) {
        return fileIndexService.listIndex(guid).stream()
                .map(
                        row -> new ManifestEntryDto(
                                row.getId().getRelativePath(),
                                row.getSha256Hex(),
                                row.getSizeBytes(),
                                row.getLastModifiedEpochMs()))
                .toList();
    }

    @PutMapping(value = "/{guid}/files/{*relativePath}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<Void> putFile(
            @PathVariable String guid, @PathVariable("relativePath") String relativePath, @RequestBody byte[] body)
            throws IOException {
        String decoded = UriUtils.decode(relativePath, StandardCharsets.UTF_8);
        inboundMirrorService.writeRemoteFile(guid, decoded, body);
        return ResponseEntity.accepted().build();
    }

    @DeleteMapping("/{guid}/files/{*relativePath}")
    public ResponseEntity<Void> deleteFile(@PathVariable String guid, @PathVariable("relativePath") String relativePath)
            throws IOException {
        String decoded = UriUtils.decode(relativePath, StandardCharsets.UTF_8);
        inboundMirrorService.deleteRemoteFile(guid, decoded);
        return ResponseEntity.noContent().build();
    }
}
