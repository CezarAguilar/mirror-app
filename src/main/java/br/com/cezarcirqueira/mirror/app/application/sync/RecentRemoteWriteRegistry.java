package br.com.cezarcirqueira.mirror.app.application.sync;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class RecentRemoteWriteRegistry {

    private final ConcurrentHashMap<String, Long> expiresByKey = new ConcurrentHashMap<>();

    public void markRemoteWrite(String mirrorGuid, String relativePath) {
        String key = mirrorGuid + "::" + relativePath;
        expiresByKey.put(key, Instant.now().toEpochMilli() + 3000);
    }

    public boolean isRecentRemoteWrite(String mirrorGuid, String relativePath) {
        String key = mirrorGuid + "::" + relativePath;
        Long exp = expiresByKey.get(key);
        if (exp == null) {
            return false;
        }
        if (Instant.now().toEpochMilli() > exp) {
            expiresByKey.remove(key);
            return false;
        }
        return true;
    }
}
