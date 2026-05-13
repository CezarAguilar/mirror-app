package br.com.cezarcirqueira.mirror.app.application.sync;

import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.FileIndexEntryRepository;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.PauseBaselineEntryRepository;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity.FileIndexEntryEntity;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity.FileIndexEntryId;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity.PauseBaselineEntryEntity;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PauseBaselineService {

    private final FileIndexEntryRepository fileIndexEntryRepository;
    private final PauseBaselineEntryRepository pauseBaselineEntryRepository;

    public PauseBaselineService(
            FileIndexEntryRepository fileIndexEntryRepository,
            PauseBaselineEntryRepository pauseBaselineEntryRepository) {
        this.fileIndexEntryRepository = fileIndexEntryRepository;
        this.pauseBaselineEntryRepository = pauseBaselineEntryRepository;
    }

    @Transactional
    public void captureBaseline(String mirrorGuid) {
        pauseBaselineEntryRepository.deleteAllByMirrorGuid(mirrorGuid);
        List<FileIndexEntryEntity> rows = fileIndexEntryRepository.findByIdMirrorGuid(mirrorGuid);
        for (FileIndexEntryEntity row : rows) {
            FileIndexEntryId id = row.getId();
            pauseBaselineEntryRepository.save(new PauseBaselineEntryEntity(id, row.getSha256Hex()));
        }
    }

    @Transactional
    public void clearBaseline(String mirrorGuid) {
        pauseBaselineEntryRepository.deleteAllByMirrorGuid(mirrorGuid);
    }
}
