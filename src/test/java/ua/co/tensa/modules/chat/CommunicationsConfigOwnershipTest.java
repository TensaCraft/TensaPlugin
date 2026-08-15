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

        String chatsYaml = Files.readString(tempDir.resolve("communications/chats.yml"), StandardCharsets.UTF_8);
        String discordYaml = Files.readString(tempDir.resolve("communications/discord.yml"), StandardCharsets.UTF_8);

        assertThat(chatsYaml).contains("enabled: true", "global:", "staff:", "alert:", "private:", "reply:");
        assertThat(chatsYaml).doesNotContain(
                "proxy:", "proxy_chat:", "discord_format:", "relay_to_discord:");
        assertThat(discordYaml).contains(
                "enabled: false",
                "proxy_chat:",
                "excluded_servers:",
                "server_aliases:",
                "discord_format:",
                "channels:"
        );
    }

    @Test
    void knownPrivateMessageDefaultsAreUpgradedWithoutOverwritingManualFormats() throws Exception {
        Tensa.pluginPath = tempDir;
        ChatConfig initial = new ChatConfig();
        initial.reloadCfg();
        String legacyTo = initial.getString("private.to_format", "")
                .replace("{message_payload}", "{message}");
        initial.getConfig().node("private", "to_format").set(legacyTo);
        initial.getConfig().node("reply", "to_format").set("<gray>custom {message}</gray>");
        initial.save();

        ChatConfig reloaded = new ChatConfig();
        reloaded.reloadCfg();

        assertThat(reloaded.getString("private.to_format", ""))
                .contains("copy_to_clipboard:'{message_payload}'")
                .contains("<#F4F7FF>{message}</#F4F7FF>");
        assertThat(reloaded.getString("reply.to_format", ""))
                .isEqualTo("<gray>custom {message}</gray>");
    }

    @Test
    void knownPrivateCommandDefaultAddsPmWithoutOverwritingManualCommands() throws Exception {
        Tensa.pluginPath = tempDir;
        ChatConfig initial = new ChatConfig();
        initial.reloadCfg();
        initial.getConfig().node("private", "command").set("msg,tell,w");
        initial.getConfig().node("reply", "command").set("customreply");
        initial.save();

        ChatConfig reloaded = new ChatConfig();
        reloaded.reloadCfg();

        assertThat(reloaded.getString("private.command", "")).isEqualTo("pm,msg,tell,w,m");
        assertThat(reloaded.getString("reply.command", "")).isEqualTo("customreply");
    }
}
