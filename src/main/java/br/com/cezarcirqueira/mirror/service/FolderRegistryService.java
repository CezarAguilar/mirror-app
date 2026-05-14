package br.com.cezarcirqueira.mirror.service;

import br.com.cezarcirqueira.mirror.domain.FolderMapping;
import br.com.cezarcirqueira.mirror.domain.SyncFolder;
import br.com.cezarcirqueira.mirror.repository.FolderMappingRepository;
import br.com.cezarcirqueira.mirror.repository.SyncFolderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class FolderRegistryService {

    private final SyncFolderRepository syncFolderRepo;
    private final FolderMappingRepository mappingRepo;

    public FolderRegistryService(SyncFolderRepository syncFolderRepo,
                                  FolderMappingRepository mappingRepo) {
        this.syncFolderRepo = syncFolderRepo;
        this.mappingRepo = mappingRepo;
    }

    public List<SyncFolder> listMasterFolders() {
        return syncFolderRepo.findAll();
    }

    @Transactional
    public SyncFolder addMasterFolder(String folderPath) {
        SyncFolder folder = new SyncFolder();
        folder.setGuid(UUID.randomUUID().toString());
        folder.setFolderPath(folderPath);
        return syncFolderRepo.save(folder);
    }

    @Transactional
    public void removeMasterFolder(String guid) {
        syncFolderRepo.findByGuid(guid).ifPresent(syncFolderRepo::delete);
    }

    public Optional<SyncFolder> findMasterFolderByGuid(String guid) {
        return syncFolderRepo.findByGuid(guid);
    }

    public List<FolderMapping> listSlaveMappings() {
        return mappingRepo.findAll();
    }

    @Transactional
    public FolderMapping upsertMapping(String guid, String remoteAddress) {
        FolderMapping mapping = mappingRepo.findByGuid(guid).orElseGet(FolderMapping::new);
        mapping.setGuid(guid);
        mapping.setRemoteAddress(remoteAddress);
        return mappingRepo.save(mapping);
    }

    @Transactional
    public FolderMapping setLocalPath(String guid, String localPath) {
        FolderMapping mapping = mappingRepo.findByGuid(guid)
                .orElseThrow(() -> new IllegalArgumentException("Mapping not found: " + guid));
        mapping.setLocalPath(localPath);
        return mappingRepo.save(mapping);
    }

    @Transactional
    public void removeLocalPath(String guid) {
        mappingRepo.findByGuid(guid).ifPresent(m -> {
            m.setLocalPath(null);
            mappingRepo.save(m);
        });
    }

    public Optional<FolderMapping> findMappingByGuid(String guid) {
        return mappingRepo.findByGuid(guid);
    }

    public List<FolderMapping> listActiveMappings() {
        return mappingRepo.findAllByLocalPathIsNotNull();
    }
}
