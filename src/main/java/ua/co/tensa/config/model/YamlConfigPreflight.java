package ua.co.tensa.config.model;

import java.nio.file.Files;
import java.nio.file.Path;

/** Read-only YAML syntax validation for transactional module reload plans. */
public final class YamlConfigPreflight {
    private YamlConfigPreflight() {
    }

    public static void validate(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException(path.getFileName() + " is not a regular file");
        }
        try {
            YamlFileIO.load(YamlFileIO.loader(path));
        } catch (Exception error) {
            throw new IllegalStateException(path.getFileName() + " contains invalid YAML", error);
        }
    }
}
