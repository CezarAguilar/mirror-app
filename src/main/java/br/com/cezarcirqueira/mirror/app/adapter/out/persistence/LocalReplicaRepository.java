package br.com.cezarcirqueira.mirror.app.adapter.out.persistence;

import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity.LocalReplicaEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocalReplicaRepository extends JpaRepository<LocalReplicaEntity, String> {

    Optional<LocalReplicaEntity> findByMirrorGuid(String mirrorGuid);
}
