package br.com.cezarcirqueira.mirror.app.adapter.out.persistence;

import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity.MirrorDefinitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MirrorDefinitionRepository extends JpaRepository<MirrorDefinitionEntity, String> {}
