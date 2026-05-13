package br.com.cezarcirqueira.mirror.app.adapter.out.persistence;

import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity.PeerEndpointEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PeerEndpointRepository extends JpaRepository<PeerEndpointEntity, Long> {}
