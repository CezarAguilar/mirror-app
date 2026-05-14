package br.com.cezarcirqueira.mirror.repository;

import br.com.cezarcirqueira.mirror.domain.AppSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSettingsRepository extends JpaRepository<AppSettings, Long> {}
