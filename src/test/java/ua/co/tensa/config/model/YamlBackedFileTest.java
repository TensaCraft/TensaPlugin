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
        assertThat(tempDir.resolve("backups/corrupt/config.yml.corrupt")).exists();
    }

    @Test
    void autoUpdateFailureDoesNotMarkValidYamlAsCorrupt() throws IOException {
        Tensa.pluginPath = tempDir;
        Path file = tempDir.resolve("config.yml");
        Files.writeString(file, "existing: \"<green>Hello</green>\"\n", StandardCharsets.UTF_8);

        new BrokenYamlFile("config.yml");

        assertThat(Files.readString(file, StandardCharsets.UTF_8)).contains("<green>Hello</green>");
        assertThat(tempDir.resolve("backups/corrupt/config.yml.corrupt")).doesNotExist();
    }

    @Test
    void repeatedUpdatesKeepOnlyOneBackupPerConfig() throws IOException {
        Tensa.pluginPath = tempDir;
        Path file = tempDir.resolve("nested/settings.yml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "existing: first\n", StandardCharsets.UTF_8);

        new TestYamlFile("nested/settings.yml");
        Files.writeString(file, "existing: second\n", StandardCharsets.UTF_8);
        new TestYamlFile("nested/settings.yml");

        Path backup = tempDir.resolve("backups/configs/nested/settings.yml.bak");
        assertThat(backup).exists();
        assertThat(Files.readString(backup, StandardCharsets.UTF_8)).contains("existing: second");
        try (var files = Files.walk(tempDir.resolve("backups/configs"))) {
            assertThat(files.filter(Files::isRegularFile).toList()).containsExactly(backup);
        }
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
