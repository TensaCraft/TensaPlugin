package ua.co.tensa.modules.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.co.tensa.Tensa;
import ua.co.tensa.modules.chat.data.ChatConfig;
import ua.co.tensa.modules.discord.data.DiscordConfig;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CommunicationsConfigOwnershipTest {
    @TempDir
    Path tempDir;

    @Test
    void generatedFilesHaveOneOwnerForProxyAndDiscordSettings() throws Exception {
        Tensa.pluginPath = tempDir;
        ChatConfig chats = new ChatConfig();
        chats.reloadCfg();
        DiscordConfig discord = new DiscordConfig();
        discord.reloadCfg();

        String chatsYaml = Files.readString(tempDir.resolve("chats.yml"), StandardCharsets.UTF_8);
        String discordYaml = Files.readString(tempDir.resolve("discord.yml"), StandardCharsets.UTF_8);

        assertThat(chatsYaml).contains("global:", "staff:", "alert:", "private:", "reply:");
        assertThat(chatsYaml).doesNotContain("proxy:", "proxy_chat:", "discord_format:");
        assertThat(discordYaml).contains(
                "enabled: false",
                "proxy_chat:",
                "excluded_servers:",
                "server_aliases:",
                "discord_format:"
        );
    }
}
