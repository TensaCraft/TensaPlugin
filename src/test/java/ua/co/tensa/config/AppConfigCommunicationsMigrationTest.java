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
    void removesRetiredAuthFlagWithoutTouchingExistingBridgeFile() throws Exception {
        Tensa.pluginPath = tempDir;
        Files.writeString(tempDir.resolve("config.yml"), """
                modules:
                  librelogin-auth-bridge: true
                  communications: false
                """);
        Path oldConfig = tempDir.resolve("auth-bridge/config.yml");
        Files.createDirectories(oldConfig.getParent());
        Files.writeString(oldConfig, "retired: retained-for-operator\n");
        Config config = new Config();
        assertThat(config.getModules()).doesNotContain("librelogin-auth-bridge");
        assertThat(config.isModuleEnabled("communications")).isFalse();
        var saved = YamlConfigurationLoader.builder().path(tempDir.resolve("config.yml")).build().load();
        assertThat(saved.node("modules", "librelogin-auth-bridge").virtual()).isTrue();
        assertThat(Files.readString(oldConfig)).isEqualTo("retired: retained-for-operator\n");
    }

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
        assertThat(config.getModules()).contains("proxy-bridge", "scheduler")
                .doesNotContain("librelogin-auth-bridge");
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

    @Test
    void migratesLegacyRequestsIdWithoutChangingItsValue() throws Exception {
        Tensa.pluginPath = tempDir;
        Files.writeString(tempDir.resolve("config.yml"), """
                modules:
                  request-module: false
                """);

        Config config = new Config();

        assertThat(config.isModuleEnabled("requests")).isFalse();
        var saved = YamlConfigurationLoader.builder().path(tempDir.resolve("config.yml")).build().load();
        assertThat(saved.node("modules", "requests").getBoolean()).isFalse();
        assertThat(saved.node("modules", "request-module").virtual()).isTrue();
    }

    @Test
    void explicitRequestsIdWinsWhenBothIdsExist() throws Exception {
        Tensa.pluginPath = tempDir;
        Files.writeString(tempDir.resolve("config.yml"), """
                modules:
                  request-module: true
                  requests: false
                """);

        Config config = new Config();

        assertThat(config.isModuleEnabled("requests")).isFalse();
        var saved = YamlConfigurationLoader.builder().path(tempDir.resolve("config.yml")).build().load();
        assertThat(saved.node("modules", "requests").getBoolean()).isFalse();
        assertThat(saved.node("modules", "request-module").virtual()).isTrue();
    }
}
