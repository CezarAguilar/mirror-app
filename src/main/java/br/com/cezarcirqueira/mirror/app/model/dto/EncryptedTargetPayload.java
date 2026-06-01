package br.com.cezarcirqueira.mirror.app.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Structured payload expected once the {@code X-Target-Encrypted} header is
 * decrypted. Carries the requested relative path plus the freshness signals
 * consumed by {@code ReplayProtectionService}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EncryptedTargetPayload {

    private String path;

    private String nonce;

    private Instant timestamp;
}
