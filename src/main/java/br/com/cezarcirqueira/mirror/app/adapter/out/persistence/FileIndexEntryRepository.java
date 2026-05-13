package br.com.cezarcirqueira.mirror.app.adapter.out.persistence;

import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity.FileIndexEntryEntity;
import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity.FileIndexEntryId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FileIndexEntryRepository extends JpaRepository<FileIndexEntryEntity, FileIndexEntryId> {

    List<FileIndexEntryEntity> findByIdMirrorGuid(String mirrorGuid);

    @Modifying
    @Query("delete from FileIndexEntryEntity f where f.id.mirrorGuid = :guid")
    void deleteAllByMirrorGuid(@Param("guid") String mirrorGuid);
}
