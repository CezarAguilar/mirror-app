package br.com.cezarcirqueira.mirror.app.resources;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/network")
public class NetworkResource {

    @GetMapping("/local-address")
    public ResponseEntity<Map<String, String>> getLocalAddress() {
        return ResponseEntity.ok(Map.of("address", resolveLocalAddress()));
    }

    private String resolveLocalAddress() {
        try {
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (networkInterface.isLoopback() || !networkInterface.isUp() || networkInterface.isVirtual()) {
                    continue;
                }
                for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            log.warn("Could not resolve local address: {}", e.getMessage());
            return "127.0.0.1";
        }
    }
}
