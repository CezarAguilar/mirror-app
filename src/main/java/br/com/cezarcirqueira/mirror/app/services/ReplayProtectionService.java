package br.com.cezarcirqueira.mirror.app.services;

import java.time.Instant;

public interface ReplayProtectionService {

    void validateAndConsume(String nonce, Instant requestTimestamp);
}
