package br.com.cezarcirqueira.mirror.app.resources;

import br.com.cezarcirqueira.mirror.app.model.dto.SyncFolderRequest;
import br.com.cezarcirqueira.mirror.app.model.dto.SyncFolderResponse;
import br.com.cezarcirqueira.mirror.app.services.SyncFolderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/sync-folders")
@RequiredArgsConstructor
public class SyncFolderResource {

    private final SyncFolderService service;

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
}
