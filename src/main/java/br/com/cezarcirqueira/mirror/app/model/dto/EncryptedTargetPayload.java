package br.com.cezarcirqueira.mirror.app.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EncryptedTargetPayload {

    private String path;

    private String nonce;

    private Instant timestamp;
}
