package ua.co.tensa.config.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YamlConfigPreflightTest {
    @TempDir
    Path tempDir;

    @Test
    void malformedYamlIsRejectedWithoutChangingTheFile() throws Exception {
        Path config = tempDir.resolve("broken.yml");
        String malformed = "root: [unterminated";
        Files.writeString(config, malformed, StandardCharsets.UTF_8);

        assertThatThrownBy(() -> YamlConfigPreflight.validate(config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid YAML");
        assertThat(Files.readString(config, StandardCharsets.UTF_8)).isEqualTo(malformed);
    }
}
