package br.com.cezarcirqueira.mirror.app.resources;

import br.com.cezarcirqueira.mirror.app.services.CryptoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/crypto")
@RequiredArgsConstructor
public class CryptoResource {

    private final CryptoService service;

    @GetMapping(value = "/public-key", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getPublicKey() {
        return ResponseEntity.ok(service.getPublicKeyPem());
    }
}
