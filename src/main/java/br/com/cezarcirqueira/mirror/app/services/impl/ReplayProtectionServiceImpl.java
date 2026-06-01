package br.com.cezarcirqueira.mirror.app.services.impl;

import br.com.cezarcirqueira.mirror.app.exceptions.ReplayAttemptException;
import br.com.cezarcirqueira.mirror.app.services.ReplayProtectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ReplayProtectionServiceImpl implements ReplayProtectionService {

    static final Duration ACCEPTABLE_CLOCK_SKEW = Duration.ofSeconds(60);
    static final Duration NONCE_RETENTION = Duration.ofMinutes(5);

    private final Map<String, Instant> consumedNonces = new ConcurrentHashMap<>();

    @Override
    public void validateAndConsume(String nonce, Instant requestTimestamp) {
        if (nonce == null || nonce.isBlank()) {
            throw new ReplayAttemptException("Missing nonce");
        }
        if (requestTimestamp == null) {
            throw new ReplayAttemptException("Missing request timestamp");
        }

        Instant now = Instant.now();
        Duration delta = Duration.between(requestTimestamp, now).abs();
        if (delta.compareTo(ACCEPTABLE_CLOCK_SKEW) > 0) {
            throw new ReplayAttemptException("Request timestamp outside acceptable window");
        }

        pruneExpired(now);

        Instant expiresAt = now.plus(NONCE_RETENTION);
        consumedNonces.compute(nonce, (key, currentExpiry) -> {
            if (currentExpiry != null && currentExpiry.isAfter(now)) {
                throw new ReplayAttemptException("Replay detected: nonce already used");
            }
            return expiresAt;
        });
    }

    private void pruneExpired(Instant now) {
        consumedNonces.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
    }
}
