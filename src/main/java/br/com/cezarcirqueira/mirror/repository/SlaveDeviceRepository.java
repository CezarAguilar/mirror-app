package br.com.cezarcirqueira.mirror.repository;

import br.com.cezarcirqueira.mirror.domain.SlaveDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SlaveDeviceRepository extends JpaRepository<SlaveDevice, Long> {
    Optional<SlaveDevice> findByIpAddress(String ipAddress);
}
