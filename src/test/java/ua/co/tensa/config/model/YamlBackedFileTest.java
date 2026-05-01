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
    void failedAutoUpdateSaveKeepsExistingFileInPlace() throws IOException {
        Tensa.pluginPath = tempDir;
        Path file = tempDir.resolve("config.yml");
        Files.writeString(file, "existing: value\n", StandardCharsets.UTF_8);

        new FailingSaveYamlFile("config.yml");

        assertThat(file).exists();
        assertThat(Files.readString(file, StandardCharsets.UTF_8)).contains("existing: value");
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

    private static final class FailingSaveYamlFile extends YamlBackedFile {
        private FailingSaveYamlFile(String relativePath) {
            super(relativePath);
        }

        @Override
        protected void populateConfigFile() {
            setConfigValue("new_key", "value");
            this.yamlFile = new ThrowingYamlFile(FILE_PATH);
        }
    }

    private static final class ThrowingYamlFile extends org.simpleyaml.configuration.file.YamlFile {
        private ThrowingYamlFile(String filePath) {
            super(filePath);
        }

        @Override
        public String saveToString() throws IOException {
            throw new IOException("simulated save failure");
        }
    }
}
