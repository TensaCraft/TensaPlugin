package ua.co.tensa.config.model;

import ua.co.tensa.Tensa;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Keeps one recoverable copy per config in the plugin backup directory. */
final class ConfigBackupStore {
    private ConfigBackupStore() {
    }

    static Path copyLatest(Path source) throws IOException {
        Path target = target(source, "configs", ".bak");
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        return target;
    }

    static Path moveCorrupt(Path source) throws IOException {
        Path target = target(source, "corrupt", ".corrupt");
        Files.createDirectories(target.getParent());
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    private static Path target(Path source, String category, String suffix) {
        Path root = Tensa.pluginPath.toAbsolutePath().normalize();
        Path normalized = source.toAbsolutePath().normalize();
        Path relative = normalized.startsWith(root)
                ? root.relativize(normalized)
                : Path.of(normalized.getFileName().toString());
        Path parent = relative.getParent();
        String fileName = relative.getFileName() + suffix;
        return parent == null
                ? root.resolve("backups").resolve(category).resolve(fileName)
                : root.resolve("backups").resolve(category).resolve(parent).resolve(fileName);
    }
}
