package ua.co.tensa.modules.discord;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.configurate.CommentedConfigurationNode;
import ua.co.tensa.config.model.YamlFileIO;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CommunicationsConfigMigrationTest {
    @TempDir
    Path tempDir;

    @Test
    void migratesLegacyModuleStatesAndProxyChatWithoutOverwritingNewManualValues() throws Exception {
        Files.writeString(tempDir.resolve("config.yml"), """
                modules:
                  chat-manager: false
                  discord: true
                  rcon-server: false
                """, StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("chats.yml"), """
                proxy:
                  enabled: false
                  excluded_servers: [auth, aero-auth]
                  server_aliases:
                    aeronautics: My Aero
                  max_length: 99
                  cooldown_millis: 321
                  duplicate_window_millis: 654
                  max_repeated_characters: 3
                  format: "<green>{message}</green>"
                  discord_format: "<blue>{message}</blue>"
                  cooldown_message: "manual cooldown"
                  duplicate_message: "manual duplicate"
                global:
                  enabled: true
                  format: "manual global"
                """, StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("discord.yml"), """
                guild_id: "12345678901234567"
                proxy_chat:
                  max_length: 777
                """, StandardCharsets.UTF_8);
        Path links = tempDir.resolve("discord").resolve("links.json");
        Files.createDirectories(links.getParent());
        Files.writeString(links, "{\"version\":1,\"links\":[]}", StandardCharsets.UTF_8);

        CommunicationsConfigMigration.Result first = CommunicationsConfigMigration.migrate(tempDir);
        String afterFirstRun = Files.readString(tempDir.resolve("discord.yml"), StandardCharsets.UTF_8);
        CommunicationsConfigMigration.Result second = CommunicationsConfigMigration.migrate(tempDir);

        CommentedConfigurationNode app = load("config.yml");
        assertThat(app.node("modules", "communications").getBoolean()).isTrue();
        assertThat(app.node("modules", "chat-manager").virtual()).isTrue();
        assertThat(app.node("modules", "discord").virtual()).isTrue();
        assertThat(app.node("modules", "rcon-server").getBoolean()).isFalse();

        CommentedConfigurationNode discord = load("discord.yml");
        assertThat(discord.node("enabled").getBoolean()).isTrue();
        assertThat(discord.node("guild_id").getString()).isEqualTo("12345678901234567");
        assertThat(discord.node("proxy_chat", "enabled").getBoolean()).isFalse();
        assertThat(discord.node("proxy_chat", "max_length").getInt()).isEqualTo(777);
        assertThat(discord.node("proxy_chat", "cooldown_millis").getLong()).isEqualTo(321L);
        assertThat(discord.node("proxy_chat", "format").getString()).isEqualTo("<green>{message}</green>");
        assertThat(discord.node("proxy_chat", "server_aliases", "aeronautics").getString())
                .isEqualTo("My Aero");

        CommentedConfigurationNode chats = load("chats.yml");
        assertThat(chats.node("proxy").virtual()).isTrue();
        assertThat(chats.node("global", "format").getString()).isEqualTo("manual global");
        assertThat(Files.readString(links, StandardCharsets.UTF_8)).isEqualTo("{\"version\":1,\"links\":[]}");

        assertThat(first.changed()).isTrue();
        assertThat(second.changed()).isFalse();
        assertThat(Files.readString(tempDir.resolve("discord.yml"), StandardCharsets.UTF_8))
                .isEqualTo(afterFirstRun);
    }

    private CommentedConfigurationNode load(String relative) throws Exception {
        Path file = tempDir.resolve(relative);
        return YamlFileIO.load(YamlFileIO.loader(file));
    }
}
