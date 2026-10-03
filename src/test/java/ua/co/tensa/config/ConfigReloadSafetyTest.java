package ua.co.tensa.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.co.tensa.Tensa;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigReloadSafetyTest {
    @TempDir Path directory;

    @Test
    void malformedYamlKeepsPreviousSettingsAndOriginalFile() throws Exception {
        Config config = initialConfig();
        String malformed = "modules: [\n";
        Files.writeString(directory.resolve("config.yml"), malformed);
        assertThatThrownBy(config::reload).isInstanceOf(IllegalStateException.class);
        assertThat(config.getLang()).isEqualTo("uk");
        assertThat(config.isModuleEnabled("scheduler")).isFalse();
        assertThat(Files.readString(directory.resolve("config.yml"))).isEqualTo(malformed);
    }

    @Test
    void invalidModuleFlagRejectsWholeCandidateBeforePublishingOrSaving() throws Exception {
        Config config = initialConfig();
        String invalid = "language: en\nmodules:\n  scheduler: maybe\n";
        Files.writeString(directory.resolve("config.yml"), invalid);
        assertThatThrownBy(config::reload).isInstanceOf(IllegalStateException.class);
        assertThat(config.getLang()).isEqualTo("uk");
        assertThat(config.isModuleEnabled("scheduler")).isFalse();
        assertThat(Files.readString(directory.resolve("config.yml"))).isEqualTo(invalid);
    }

    private Config initialConfig() throws Exception {
        Tensa.pluginPath = directory;
        Files.writeString(directory.resolve("config.yml"), "language: uk\nmodules:\n  scheduler: false\n");
        return new Config();
    }
}
