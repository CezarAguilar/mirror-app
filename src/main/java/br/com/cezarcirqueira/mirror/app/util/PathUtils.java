package br.com.cezarcirqueira.mirror.app.util;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class PathUtils {

    private PathUtils() {
    }

    public static String preClean(String input) {
        if (input == null) {
            return "";
        }
        return input.trim().replaceAll("[\\\\/]+", "/");
    }

    public static Path resolve(String input) {
        String cleaned = preClean(input);
        if (cleaned.isBlank()) {
            throw new IllegalArgumentException("Path is required");
        }
        try {
            return Paths.get(cleaned).toAbsolutePath().normalize();
        } catch (InvalidPathException ex) {
            throw new IllegalArgumentException("Invalid path: " + input);
        }
    }

    public static String toCanonical(Path path) {
        return path == null ? null : path.toString().replace('\\', '/');
    }

    public static String canonicalize(String input) {
        return toCanonical(resolve(input));
    }
}
