package br.com.cezarcirqueira.mirror.app.adapter.out.persistence;

import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity.PauseBaselineEntryEntity;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity.FileIndexEntryId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PauseBaselineEntryRepository extends JpaRepository<PauseBaselineEntryEntity, FileIndexEntryId> {

    List<PauseBaselineEntryEntity> findByIdMirrorGuid(String mirrorGuid);

    @Modifying
    @Query("delete from PauseBaselineEntryEntity p where p.id.mirrorGuid = :guid")
    void deleteAllByMirrorGuid(@Param("guid") String mirrorGuid);
}
