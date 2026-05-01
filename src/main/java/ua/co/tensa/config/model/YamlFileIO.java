package ua.co.tensa.config.model;

import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class YamlFileIO {
    private YamlFileIO() {
    }

    public static YamlConfigurationLoader loader(Path path) {
        return YamlConfigurationLoader.builder()
                .path(path)
                .indent(2)
                .nodeStyle(NodeStyle.BLOCK)
                .build();
    }

    public static CommentedConfigurationNode load(YamlConfigurationLoader loader) throws ConfigurateException {
        return loader.load();
    }

    public static void saveValidated(YamlConfigurationLoader loader, CommentedConfigurationNode node, Path target) throws IOException {
        Path parent = target.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path temp = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
        try {
            YamlConfigurationLoader tempLoader = loader(temp);
            tempLoader.save(node);
            preserveDetachedComments(target, temp);
            tempLoader.load();

            if (Files.exists(target) && sameContent(target, temp)) {
                Files.deleteIfExists(temp);
                return;
            }

            if (Files.exists(target)) {
                Path backup = target.resolveSibling(target.getFileName() + ".bak." + System.currentTimeMillis());
                Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
            }

            moveIntoPlace(temp, target);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
    }

    private static boolean sameContent(Path left, Path right) throws IOException {
        return Files.readString(left, StandardCharsets.UTF_8).equals(Files.readString(right, StandardCharsets.UTF_8));
    }

    private static void preserveDetachedComments(Path original, Path generated) throws IOException {
        if (!Files.exists(original)) {
            return;
        }

        String generatedText = Files.readString(generated, StandardCharsets.UTF_8);
        StringBuilder missingComments = new StringBuilder();
        for (String line : Files.readAllLines(original, StandardCharsets.UTF_8)) {
            if (line.trim().startsWith("#") && !generatedText.contains(line)) {
                missingComments.append(line).append(System.lineSeparator());
            }
        }

        if (missingComments.isEmpty()) {
            return;
        }

        Files.writeString(generated, missingComments.append(generatedText).toString(), StandardCharsets.UTF_8);
    }

    private static void moveIntoPlace(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
