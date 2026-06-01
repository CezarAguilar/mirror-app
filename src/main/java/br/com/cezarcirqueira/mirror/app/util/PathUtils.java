package br.com.cezarcirqueira.mirror.app.util;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Path-handling utilities.
 *
 * <p>The application stores and exchanges paths in a single canonical form:
 * <em>absolute, normalized, with {@code /} as separator</em>. {@link Path}
 * accepts forward slashes on Windows and Unix alike, so this is portable.</p>
 *
 * <p>Inputs are intentionally tolerant: any mix of {@code /}, {@code \} or
 * runs of either separator are collapsed before being parsed by {@link Paths}.</p>
 */
public final class PathUtils {

    private PathUtils() {
    }

    /**
     * Trims and collapses any run of {@code /} or {@code \} into a single {@code /}.
     * Returns an empty string when {@code input} is null or blank.
     */
    public static String preClean(String input) {
        if (input == null) {
            return "";
        }
        return input.trim().replaceAll("[\\\\/]+", "/");
    }

    /**
     * Parses an arbitrary user-supplied path, makes it absolute and normalized.
     * Throws {@link IllegalArgumentException} (mapped to HTTP 400 by the
     * global advice) when the value cannot be parsed.
     */
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

    /**
     * Canonical string representation (forward slashes) of a {@link Path}.
     */
    public static String toCanonical(Path path) {
        return path == null ? null : path.toString().replace('\\', '/');
    }

    /**
     * Convenience: {@link #resolve(String)} + {@link #toCanonical(Path)}.
     */
    public static String canonicalize(String input) {
        return toCanonical(resolve(input));
    }
}
