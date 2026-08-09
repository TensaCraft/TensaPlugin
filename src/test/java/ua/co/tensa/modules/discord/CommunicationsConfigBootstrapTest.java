package ua.co.tensa.modules.discord;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.configurate.CommentedConfigurationNode;
import ua.co.tensa.config.model.YamlFileIO;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommunicationsConfigBootstrapTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-04T12:34:56Z"), ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    @Test
    void archivesLegacyCommunicationsFilesAndCreatesCleanVersionTwoConfigs() throws Exception {
        Files.writeString(tempDir.resolve("discord.yml"), "guild_id: old\nbot:\n  token: secret-value\n", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("chats.yml"), "global:\n  format: old-format\n", StandardCharsets.UTF_8);
        Path links = tempDir.resolve("discord").resolve("links.json");
        Files.createDirectories(links.getParent());
        Files.writeString(links, "{\"version\":1,\"links\":[{\"secret\":true}]}", StandardCharsets.UTF_8);

        CommunicationsConfigBootstrap.Result result =
                new CommunicationsConfigBootstrap(CLOCK).prepare(tempDir);

        assertThat(result.reset()).isTrue();
        assertThat(result.archivedFiles()).containsExactlyInAnyOrder("discord.yml", "chats.yml", "discord/links.json");
        assertThat(load("communications/discord.yml").node("config_version").getInt()).isEqualTo(2);
        assertThat(load("communications/discord.yml").node("guild_id").virtual()).isTrue();
        assertThat(load("communications/chats.yml").node("config_version").getInt()).isEqualTo(2);
        assertThat(load("communications/chats.yml").node("global").virtual()).isTrue();
        assertThat(Files.exists(links)).isFalse();

        Path backup = tempDir.resolve("backups").resolve("communications-20260804-123456Z");
        assertThat(Files.readString(backup.resolve("discord.yml"))).contains("secret-value");
        assertThat(Files.readString(backup.resolve("chats.yml"))).contains("old-format");
        assertThat(Files.readString(backup.resolve("discord").resolve("links.json"))).contains("\"secret\":true");
    }

    @Test
    void createsVersionTwoFilesForBrandNewInstallationWithoutBackup() throws Exception {
        CommunicationsConfigBootstrap.Result result =
                new CommunicationsConfigBootstrap(CLOCK).prepare(tempDir);

        assertThat(result.reset()).isFalse();
        assertThat(result.archivedFiles()).isEmpty();
        assertThat(load("communications/discord.yml").node("config_version").getInt()).isEqualTo(2);
        assertThat(load("communications/chats.yml").node("config_version").getInt()).isEqualTo(2);
        assertThat(Files.exists(tempDir.resolve("backups"))).isFalse();
    }

    @Test
    void relocatesSupportedRootVersionTwoFilesByteForByte() throws Exception {
        String discord = "config_version: 2\nenabled: false\n";
        String chats = "config_version: 2\nenabled: true\n";
        Files.writeString(tempDir.resolve("discord.yml"), discord, StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("chats.yml"), chats, StandardCharsets.UTF_8);

        CommunicationsConfigBootstrap.Result result =
                new CommunicationsConfigBootstrap(CLOCK).prepare(tempDir);

        assertThat(result.reset()).isFalse();
        assertThat(result.relocated()).isTrue();
        assertThat(Files.readString(tempDir.resolve("communications/discord.yml"))).isEqualTo(discord);
        assertThat(Files.readString(tempDir.resolve("communications/chats.yml"))).isEqualTo(chats);
        assertThat(tempDir.resolve("discord.yml")).doesNotExist();
        assertThat(tempDir.resolve("chats.yml")).doesNotExist();
    }

    @Test
    void rejectsFutureSchemaBeforeChangingAnyFile() throws Exception {
        String discord = "config_version: 3\nenabled: true\n";
        String chats = "config_version: 2\nenabled: true\n";
        Files.writeString(tempDir.resolve("discord.yml"), discord, StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("chats.yml"), chats, StandardCharsets.UTF_8);

        assertThatThrownBy(() -> new CommunicationsConfigBootstrap(CLOCK).prepare(tempDir))
                .isInstanceOf(DiscordConfigurationException.class)
                .hasMessageContaining("newer config_version");

        assertThat(Files.readString(tempDir.resolve("discord.yml"))).isEqualTo(discord);
        assertThat(Files.readString(tempDir.resolve("chats.yml"))).isEqualTo(chats);
        assertThat(Files.exists(tempDir.resolve("backups"))).isFalse();
    }

    @Test
    void existingModuleDirectoryWinsAndObsoleteRootCopyIsArchived() throws Exception {
        Path directory = tempDir.resolve("communications");
        Files.createDirectories(directory);
        String active = "config_version: 2\nenabled: true\n";
        Files.writeString(directory.resolve("discord.yml"), active, StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("chats.yml"), active, StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("discord.yml"), "config_version: 2\nenabled: false\n", StandardCharsets.UTF_8);

        CommunicationsConfigBootstrap.Result result =
                new CommunicationsConfigBootstrap(CLOCK).prepare(tempDir);

        assertThat(result.relocated()).isTrue();
        assertThat(Files.readString(directory.resolve("discord.yml"))).isEqualTo(active);
        assertThat(tempDir.resolve("discord.yml")).doesNotExist();
        Path backup = tempDir.resolve("backups/communications-20260804-123456Z/discord.yml");
        assertThat(backup).exists();
    }

    @Test
    void failedBackupLeavesAllOriginalsUntouched() throws Exception {
        String discord = "enabled: true\n";
        String chats = "enabled: true\n";
        Files.writeString(tempDir.resolve("discord.yml"), discord, StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("chats.yml"), chats, StandardCharsets.UTF_8);

        CommunicationsConfigBootstrap bootstrap = new CommunicationsConfigBootstrap(
                CLOCK,
                (source, target) -> {
                    if (source.getFileName().toString().equals("chats.yml")) {
                        throw new IOException("simulated backup failure");
                    }
                    CommunicationsConfigBootstrap.copyAndVerify(source, target);
                }
        );

        assertThatThrownBy(() -> bootstrap.prepare(tempDir))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("simulated backup failure");

        assertThat(Files.readString(tempDir.resolve("discord.yml"))).isEqualTo(discord);
        assertThat(Files.readString(tempDir.resolve("chats.yml"))).isEqualTo(chats);
    }

    private CommentedConfigurationNode load(String relative) throws Exception {
        return YamlFileIO.load(YamlFileIO.loader(tempDir.resolve(relative)));
    }
}
