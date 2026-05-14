package br.com.cezarcirqueira.mirror.repository;

import br.com.cezarcirqueira.mirror.domain.FolderMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FolderMappingRepository extends JpaRepository<FolderMapping, Long> {
    Optional<FolderMapping> findByGuid(String guid);
    List<FolderMapping> findAllByLocalPathIsNotNull();
}
