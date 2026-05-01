package ua.co.tensa.config.model;

import org.simpleyaml.configuration.file.YamlFile;
import org.simpleyaml.exceptions.InvalidConfigurationException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public final class YamlFileIO {

    private YamlFileIO() {
    }

    public static void loadWithComments(YamlFile yamlFile) throws IOException, InvalidConfigurationException {
        yamlFile.loadWithComments();
    }

    public static void saveValidated(YamlFile yamlFile) throws IOException {
        saveValidated(yamlFile, yamlFile.getConfigurationFile().toPath());
    }

    public static void saveValidated(YamlFile yamlFile, Path target) throws IOException {
        Path absoluteTarget = target.toAbsolutePath();
        Path directory = absoluteTarget.getParent();
        if (directory != null) {
            Files.createDirectories(directory);
        }

        String rendered = yamlFile.saveToString();
        validate(rendered);

        Path tempFile = Files.createTempFile(
                directory == null ? Path.of(".") : directory,
                absoluteTarget.getFileName().toString() + ".",
                ".tmp"
        );

        boolean moved = false;
        try {
            Files.writeString(
                    tempFile,
                    rendered,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            moveIntoPlace(tempFile, absoluteTarget);
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(tempFile);
            }
        }
    }

    private static void validate(String yaml) throws IOException {
        YamlFile.loadConfigurationFromString(yaml, true);
    }

    private static void moveIntoPlace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
