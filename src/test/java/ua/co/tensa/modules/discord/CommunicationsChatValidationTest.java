package ua.co.tensa.modules.discord;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.co.tensa.Tensa;
import ua.co.tensa.modules.chat.data.ChatConfig;
import ua.co.tensa.modules.discord.data.DiscordConfig;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommunicationsChatValidationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void rejectsNestedMapsWherePrivateMiniMessageFormatsMustBeStrings() throws Exception {
        Tensa.pluginPath = temporaryDirectory;
        Files.createDirectories(temporaryDirectory.resolve("communications"));
        Files.writeString(temporaryDirectory.resolve("communications/chats.yml"), """
                config_version: 2
                enabled: true
                private:
                  enabled: true
                  type: private
                  command: pm
                  to_format: {}
                  from_format: '{}'
                """);
        ChatConfig config = new ChatConfig();
        config.reloadCfg();

        assertThatThrownBy(() -> CommunicationsModule.validateChat(config.adapter()))
                .isInstanceOf(DiscordConfigurationException.class)
                .hasMessageContaining("private.to_format")
                .hasMessageContaining("MiniMessage string");
    }

    @Test
    void validatesDisabledRoutesBeforeTheyCanBeActivatedLater() throws Exception {
        Tensa.pluginPath = temporaryDirectory;
        Files.createDirectories(temporaryDirectory.resolve("communications"));
        Files.writeString(temporaryDirectory.resolve("communications/chats.yml"), """
                config_version: 2
                enabled: true
                private:
                  enabled: false
                  type: private
                  command: pm
                  to_format: {}
                """);
        ChatConfig config = new ChatConfig();
        config.reloadCfg();

        assertThatThrownBy(() -> CommunicationsModule.validateChat(config.adapter()))
                .isInstanceOf(DiscordConfigurationException.class)
                .hasMessageContaining("private.to_format");
    }

    @Test
    void rejectsNestedObjectsInDiscordChatPresentation() throws Exception {
        Tensa.pluginPath = temporaryDirectory;
        Files.createDirectories(temporaryDirectory.resolve("communications"));
        Files.writeString(temporaryDirectory.resolve("communications/discord.yml"), """
                config_version: 2
                bot:
                  enabled: false
                proxy_chat:
                  discord_format: {}
                """);
        DiscordConfig config = new DiscordConfig();
        config.reloadCfg();

        assertThatThrownBy(() -> CommunicationsModule.validateProxyChat(config.adapter()))
                .isInstanceOf(DiscordConfigurationException.class)
                .hasMessageContaining("proxy_chat.discord_format")
                .hasMessageContaining("MiniMessage string");
    }
}
