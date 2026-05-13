package br.com.cezarcirqueira.mirror.app.application.sync;

import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.entity.FileIndexEntryEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record RescanDelta(List<String> upsertedOrChangedRelativePaths, List<String> deletedRelativePaths) {

    public boolean isEmpty() {
        return upsertedOrChangedRelativePaths.isEmpty() && deletedRelativePaths.isEmpty();
    }

    public static RescanDelta empty() {
        return new RescanDelta(List.of(), List.of());
    }

    public static RescanDelta compute(Map<String, String> previousHashesByPath, Map<String, String> newHashesByPath) {
        Set<String> oldKeys = new HashSet<>(previousHashesByPath.keySet());
        Set<String> newKeys = new HashSet<>(newHashesByPath.keySet());
        List<String> deleted = new ArrayList<>();
        for (String p : oldKeys) {
            if (!newKeys.contains(p)) {
                deleted.add(p);
            }
        }
        List<String> changed = new ArrayList<>();
        for (String p : newKeys) {
            String nh = newHashesByPath.get(p);
            String oh = previousHashesByPath.get(p);
            if (oh == null || !oh.equals(nh)) {
                changed.add(p);
            }
        }
        return new RescanDelta(changed, deleted);
    }

    public static Map<String, String> toHashMap(List<FileIndexEntryEntity> rows) {
        Map<String, String> map = new HashMap<>();
        for (FileIndexEntryEntity row : rows) {
            map.put(row.getId().getRelativePath(), row.getSha256Hex());
        }
        return map;
    }
}
