package ua.co.tensa.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;
import ua.co.tensa.Tensa;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AppConfigCommunicationsMigrationTest {

    @TempDir
    Path tempDir;

    @Test
    void preservesDisabledLegacyStateBeforeRemovingOldFlags() throws Exception {
        Tensa.pluginPath = tempDir;
        Files.writeString(tempDir.resolve("config.yml"), """
                modules:
                  chat-manager: false
                  discord: false
                """);

        Config config = new Config();

        assertThat(config.isModuleEnabled("communications")).isFalse();
        var saved = YamlConfigurationLoader.builder().path(tempDir.resolve("config.yml")).build().load();
        assertThat(saved.node("modules", "communications").getBoolean()).isFalse();
        assertThat(saved.node("modules", "chat-manager").virtual()).isTrue();
        assertThat(saved.node("modules", "discord").virtual()).isTrue();
        assertThat(config.getModules()).contains("librelogin-auth-bridge", "proxy-bridge");
    }

    @Test
    void enablesCommunicationsWhenAnyLegacyOwnerWasEnabled() throws Exception {
        Tensa.pluginPath = tempDir;
        Files.writeString(tempDir.resolve("config.yml"), """
                modules:
                  chat: true
                  discord: false
                """);

        Config config = new Config();

        assertThat(config.isModuleEnabled("communications")).isTrue();
        var saved = YamlConfigurationLoader.builder().path(tempDir.resolve("config.yml")).build().load();
        assertThat(saved.node("modules", "communications").getBoolean()).isTrue();
        assertThat(saved.node("modules", "chat").virtual()).isTrue();
        assertThat(saved.node("modules", "discord").virtual()).isTrue();
    }

    @Test
    void explicitCommunicationsFlagWinsOverLegacyFlags() throws Exception {
        Tensa.pluginPath = tempDir;
        Files.writeString(tempDir.resolve("config.yml"), """
                modules:
                  communications: false
                  chat-manager: true
                  discord: true
                """);

        Config config = new Config();

        assertThat(config.isModuleEnabled("communications")).isFalse();
        var saved = YamlConfigurationLoader.builder().path(tempDir.resolve("config.yml")).build().load();
        assertThat(saved.node("modules", "communications").getBoolean()).isFalse();
        assertThat(saved.node("modules", "chat-manager").virtual()).isTrue();
        assertThat(saved.node("modules", "discord").virtual()).isTrue();
    }
}
