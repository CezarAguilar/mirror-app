package br.com.cezarcirqueira.mirror.app.domain;

import java.util.UUID;

/**
 * Logical mirror identifier shared across nodes (GUID).
 */
public record SyncMirrorId(UUID value) {

    public static SyncMirrorId of(UUID uuid) {
        return new SyncMirrorId(uuid);
    }

    public static SyncMirrorId parse(String raw) {
        return new SyncMirrorId(UUID.fromString(raw));
    }

    public String asString() {
        return value.toString();
    }
}
