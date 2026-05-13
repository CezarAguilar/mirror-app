package br.com.cezarcirqueira.mirror.app.adapter.out.persistence;

import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity.NodeSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NodeSettingsRepository extends JpaRepository<NodeSettingsEntity, String> {}
