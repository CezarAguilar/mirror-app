package br.com.cezarcirqueira.mirror.repository;

import br.com.cezarcirqueira.mirror.domain.SyncFolder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SyncFolderRepository extends JpaRepository<SyncFolder, Long> {
    Optional<SyncFolder> findByGuid(String guid);
    boolean existsByGuid(String guid);
}
