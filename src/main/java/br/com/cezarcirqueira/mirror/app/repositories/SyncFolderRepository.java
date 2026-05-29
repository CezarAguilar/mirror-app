package br.com.cezarcirqueira.mirror.app.repositories;

import br.com.cezarcirqueira.mirror.app.model.SyncFolder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SyncFolderRepository extends JpaRepository<SyncFolder, Long> {
    Optional<SyncFolder> findByGuid(UUID guid);
    boolean existsByGuid(UUID guid);
    void deleteByGuid(UUID guid);
}
