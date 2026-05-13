package br.com.cezarcirqueira.mirror.app.application.filesystem;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class EditableFileClassifier {

    private static final Set<String> EDITABLE_EXTENSIONS = Set.of(
            "txt",
            "md",
            "java",
            "properties",
            "xml",
            "json",
            "html",
            "css",
            "js",
            "yml",
            "yaml",
            "gradle",
            "kts",
            "sql",
            "gitignore");

    public boolean isEditable(Path relativePath) {
        String name = relativePath.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return false;
        }
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return EDITABLE_EXTENSIONS.contains(ext);
    }
}
