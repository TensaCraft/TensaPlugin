package ua.co.tensa.config.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.co.tensa.Tensa;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class YamlBackedFileTest {

    @TempDir
    Path tempDir;

    @Test
    void reloadAddsMissingDefaultsWithoutDroppingExistingCommentsOrMiniMessage() throws IOException {
        Tensa.pluginPath = tempDir;
        Path file = tempDir.resolve("config.yml");
        Files.writeString(file, """
                # User comment must survive config auto-update.
                existing: "<green>Hello</green> <response>"
                """, StandardCharsets.UTF_8);

        new TestYamlFile("config.yml");

        String saved = Files.readString(file, StandardCharsets.UTF_8);
        assertThat(saved).contains("# User comment must survive config auto-update.");
        assertThat(saved).contains("<green>Hello</green> <response>");
        assertThat(saved).contains("new_key:");
    }

    @Test
    void corruptYamlIsBackedUpAndRegenerated() throws IOException {
        Tensa.pluginPath = tempDir;
        Path file = tempDir.resolve("config.yml");
        Files.writeString(file, "root:\n  broken: 'value\n next: bad\n", StandardCharsets.UTF_8);

        new TestYamlFile("config.yml");

        assertThat(file).exists();
        assertThat(Files.readString(file, StandardCharsets.UTF_8)).contains("new_key:");
        assertThat(Files.list(tempDir)
                .filter(path -> path.getFileName().toString().startsWith("config.yml.corrupt."))
                .toList()).hasSize(1);
    }

    @Test
    void autoUpdateFailureDoesNotMarkValidYamlAsCorrupt() throws IOException {
        Tensa.pluginPath = tempDir;
        Path file = tempDir.resolve("config.yml");
        Files.writeString(file, "existing: \"<green>Hello</green>\"\n", StandardCharsets.UTF_8);

        new BrokenYamlFile("config.yml");

        assertThat(Files.readString(file, StandardCharsets.UTF_8)).contains("<green>Hello</green>");
        assertThat(Files.list(tempDir)
                .filter(path -> path.getFileName().toString().startsWith("config.yml.corrupt."))
                .toList()).isEmpty();
    }

    private static final class TestYamlFile extends YamlBackedFile {
        private TestYamlFile(String relativePath) {
            super(relativePath);
        }

        @Override
        protected void populateConfigFile() {
            setConfigValue("existing", "<red>Default</red>");
            setConfigValue("new_key", "<gold>New default</gold>");
        }
    }

    private static final class BrokenYamlFile extends YamlBackedFile {
        private BrokenYamlFile(String relativePath) {
            super(relativePath);
        }

        @Override
        protected void populateConfigFile() {
            throw new IllegalStateException("simulated default update failure");
        }
    }

}
