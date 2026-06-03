package br.com.cezarcirqueira.mirror.app.resources;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@RestController
@RequestMapping("/api/network")
public class NetworkResource {

    private static final Pattern VIRTUAL_ADAPTER_PATTERN = Pattern.compile(
            "(?i)(vethernet|vbox|virtualbox|docker|veth|virbr|vmnet|vmware|"
                    + "tun|tap|hyper-?\\s?v|wsl|teredo|isatap|tailscale|wireguard|"
                    + "bluetooth|loopback pseudo)");

    @GetMapping("/local-address")
    public ResponseEntity<Map<String, String>> getLocalAddress() {
        return ResponseEntity.ok(Map.of("address", resolveLocalAddress()));
    }

    private String resolveLocalAddress() {
        String address = resolveByDefaultRoute();
        if (address != null) {
            log.debug("Local address resolved via default route: {}", address);
            return address;
        }

        address = resolveByInterfaceScan();
        if (address != null) {
            log.debug("Local address resolved via interface scan: {}", address);
            return address;
        }

        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception ex) {
            log.warn("Could not resolve local address: {}", ex.getMessage());
            return "127.0.0.1";
        }
    }

    private String resolveByDefaultRoute() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 53);
            InetAddress local = socket.getLocalAddress();
            if (local instanceof Inet4Address
                    && !local.isAnyLocalAddress()
                    && !local.isLoopbackAddress()) {
                return local.getHostAddress();
            }
        } catch (Exception ex) {
            log.debug("Default-route lookup failed: {}", ex.getMessage());
        }
        return null;
    }

    private String resolveByInterfaceScan() {
        try {
            String siteLocal = null;
            String other = null;
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || isLikelyVirtual(ni)) {
                    continue;
                }
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (!(addr instanceof Inet4Address)
                            || addr.isLoopbackAddress()
                            || addr.isLinkLocalAddress()) {
                        continue;
                    }
                    if (addr.isSiteLocalAddress() && siteLocal == null) {
                        siteLocal = addr.getHostAddress();
                    } else if (other == null) {
                        other = addr.getHostAddress();
                    }
                }
            }
            return siteLocal != null ? siteLocal : other;
        } catch (Exception ex) {
            log.debug("Interface scan failed: {}", ex.getMessage());
            return null;
        }
    }

    private boolean isLikelyVirtual(NetworkInterface ni) throws SocketException {
        if (ni.isVirtual()) {
            return true;
        }
        String displayName = ni.getDisplayName();
        if (displayName != null && VIRTUAL_ADAPTER_PATTERN.matcher(displayName).find()) {
            return true;
        }
        String name = ni.getName();
        return name != null && VIRTUAL_ADAPTER_PATTERN.matcher(name).find();
    }
}
