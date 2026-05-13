package br.com.cezarcirqueira.mirror.app.adapter.out.persistence;

import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity.MergeConflictEntity;
import br.com.cezarcirqueira.mirror.app.domain.ConflictStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MergeConflictRepository extends JpaRepository<MergeConflictEntity, Long> {

    List<MergeConflictEntity> findByMirrorGuidAndStatus(String mirrorGuid, ConflictStatus status);

    List<MergeConflictEntity> findByStatus(ConflictStatus status);

    @Modifying
    @Query("delete from MergeConflictEntity m where m.mirrorGuid = :guid")
    void deleteAllByMirrorGuid(@Param("guid") String mirrorGuid);
}
