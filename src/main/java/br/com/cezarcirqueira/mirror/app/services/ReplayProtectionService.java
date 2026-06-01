package br.com.cezarcirqueira.mirror.app.services;

import java.time.Instant;

public interface ReplayProtectionService {

    /**
     * Validates the freshness signals of a request and consumes the nonce so it
     * cannot be reused.
     *
     * @param nonce            unique random value provided by the client
     * @param requestTimestamp instant in which the client signed the request
     * @throws br.com.cezarcirqueira.mirror.app.exceptions.ReplayAttemptException
     *         when {@code nonce} is missing/already seen or {@code requestTimestamp}
     *         is outside the acceptable window
     */
    void validateAndConsume(String nonce, Instant requestTimestamp);
}
