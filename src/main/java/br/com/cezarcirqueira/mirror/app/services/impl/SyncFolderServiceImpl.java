package br.com.cezarcirqueira.mirror.app.services.impl;

import br.com.cezarcirqueira.mirror.app.exceptions.ResourceNotFoundException;
import br.com.cezarcirqueira.mirror.app.model.SyncFolder;
import br.com.cezarcirqueira.mirror.app.model.dto.SyncFolderRequest;
import br.com.cezarcirqueira.mirror.app.model.dto.SyncFolderResponse;
import br.com.cezarcirqueira.mirror.app.repositories.SyncFolderRepository;
import br.com.cezarcirqueira.mirror.app.services.FolderWatcherService;
import br.com.cezarcirqueira.mirror.app.services.SyncFolderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SyncFolderServiceImpl implements SyncFolderService {

    private final SyncFolderRepository repository;
    private final FolderWatcherService folderWatcherService;

    @Override
    @Transactional
    public SyncFolderResponse create(SyncFolderRequest request) {
        SyncFolder entity = SyncFolder.builder()
                .guid(request.getGuid())
                .basePath(request.getBasePath())
                .build();
        
        entity = repository.save(entity);
        folderWatcherService.registerFolder(entity);
        return mapToResponse(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public SyncFolderResponse findByGuid(UUID guid) {
        SyncFolder entity = repository.findByGuid(guid)
                .orElseThrow(() -> new ResourceNotFoundException("SyncFolder not found: " + guid));
        return mapToResponse(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SyncFolderResponse> findAll() {
        return repository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public SyncFolderResponse update(UUID guid, SyncFolderRequest request) {
        SyncFolder entity = repository.findByGuid(guid)
                .orElseThrow(() -> new ResourceNotFoundException("SyncFolder not found: " + guid));
        
        folderWatcherService.unregisterFolder(guid);
        
        if (request.getGuid() != null) {
            entity.setGuid(request.getGuid());
        }
        if (request.getBasePath() != null) {
            entity.setBasePath(request.getBasePath());
        }
        
        entity = repository.save(entity);
        folderWatcherService.registerFolder(entity);
        return mapToResponse(entity);
    }

    @Override
    @Transactional
    public void delete(UUID guid) {
        if (!repository.existsByGuid(guid)) {
            throw new ResourceNotFoundException("SyncFolder not found: " + guid);
        }
        repository.deleteByGuid(guid);
        folderWatcherService.unregisterFolder(guid);
    }

    private SyncFolderResponse mapToResponse(SyncFolder entity) {
        return SyncFolderResponse.builder()
                .guid(entity.getGuid())
                .creationDate(entity.getCreationDate())
                .basePath(entity.getBasePath())
                .build();
    }
}
