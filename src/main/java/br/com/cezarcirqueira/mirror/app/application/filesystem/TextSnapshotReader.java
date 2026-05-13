package br.com.cezarcirqueira.mirror.app.application.filesystem;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class TextSnapshotReader {

    private static final int MAX_CHARS = 200_000;

    public Optional<String> readLimited(Path file, boolean editable) {
        if (!editable) {
            return Optional.empty();
        }
        try {
            if (!Files.isRegularFile(file)) {
                return Optional.empty();
            }
            long size = Files.size(file);
            if (size > MAX_CHARS * 4L) {
                return Optional.of("(file too large for text preview)");
            }
            byte[] bytes = Files.readAllBytes(file);
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (text.length() > MAX_CHARS) {
                return Optional.of(text.substring(0, MAX_CHARS) + "\n(truncated)");
            }
            return Optional.of(text);
        } catch (Exception e) {
            return Optional.of("(could not read file: " + e.getMessage() + ")");
        }
    }
}
