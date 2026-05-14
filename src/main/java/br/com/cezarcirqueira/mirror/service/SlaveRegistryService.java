package br.com.cezarcirqueira.mirror.service;

import br.com.cezarcirqueira.mirror.domain.DeviceStatus;
import br.com.cezarcirqueira.mirror.domain.SlaveDevice;
import br.com.cezarcirqueira.mirror.repository.SlaveDeviceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class SlaveRegistryService {

    private final SlaveDeviceRepository repo;

    public SlaveRegistryService(SlaveDeviceRepository repo) {
        this.repo = repo;
    }

    public List<SlaveDevice> listAll() {
        return repo.findAll();
    }

    @Transactional
    public SlaveDevice registerOrUpdate(String name, String ip, Integer port) {
        SlaveDevice device = repo.findByIpAddress(ip).orElseGet(SlaveDevice::new);
        device.setName(name);
        device.setIpAddress(ip);
        device.setPort(port);
        device.setStatus(DeviceStatus.ONLINE);
        device.setLastHeartbeat(LocalDateTime.now());
        return repo.save(device);
    }

    @Transactional
    public void recordHeartbeat(String ip) {
        repo.findByIpAddress(ip).ifPresent(device -> {
            if (device.getStatus() != DeviceStatus.PAUSED) {
                device.setStatus(DeviceStatus.ONLINE);
            }
            device.setLastHeartbeat(LocalDateTime.now());
            repo.save(device);
        });
    }

    @Transactional
    public void pauseDevice(Long id) {
        repo.findById(id).ifPresent(device -> {
            device.setStatus(DeviceStatus.PAUSED);
            repo.save(device);
        });
    }

    @Transactional
    public void reactivateDevice(Long id) {
        repo.findById(id).ifPresent(device -> {
            device.setStatus(DeviceStatus.ONLINE);
            device.setLastHeartbeat(LocalDateTime.now());
            repo.save(device);
        });
    }

    @Transactional
    public void disconnectDevice(Long id) {
        repo.deleteById(id);
    }

    @Transactional
    public void markOfflineIfStale(int timeoutSeconds) {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(timeoutSeconds);
        repo.findAll().forEach(device -> {
            if (device.getStatus() == DeviceStatus.ONLINE
                    && device.getLastHeartbeat() != null
                    && device.getLastHeartbeat().isBefore(threshold)) {
                device.setStatus(DeviceStatus.OFFLINE);
                repo.save(device);
            }
        });
    }

    public Optional<SlaveDevice> findById(Long id) {
        return repo.findById(id);
    }
}
