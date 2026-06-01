package br.com.cezarcirqueira.mirror.app.resources;

import br.com.cezarcirqueira.mirror.app.model.dto.FilesystemBrowserResponse;
import br.com.cezarcirqueira.mirror.app.services.SystemService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemResource {

    private final SystemService service;

    @GetMapping("/browse")
    public ResponseEntity<FilesystemBrowserResponse> browse(@RequestParam(required = false) String path,
                                                            HttpServletRequest request) {
        if (!isLoopback(request.getRemoteAddr())) {
            throw new SecurityException("Filesystem browser is restricted to local (loopback) connections");
        }
        return ResponseEntity.ok(service.listDirectories(path));
    }

    private static boolean isLoopback(String remoteAddr) {
        return "127.0.0.1".equals(remoteAddr)
                || "0:0:0:0:0:0:0:1".equals(remoteAddr)
                || "::1".equals(remoteAddr);
    }
}
