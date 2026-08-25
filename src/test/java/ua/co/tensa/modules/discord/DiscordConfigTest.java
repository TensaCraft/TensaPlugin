package ua.co.tensa.modules.discord;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.co.tensa.Tensa;
import ua.co.tensa.modules.discord.data.DiscordConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscordConfigTest {
    @TempDir
    Path tempDir;

    @Test
    void generatedConfigNeverWritesSecretKeys() throws IOException {
        Tensa.pluginPath = tempDir;
        DiscordConfig config = new DiscordConfig();
        config.reloadCfg();

        String yaml = Files.readString(tempDir.resolve("communications/discord.yml"), StandardCharsets.UTF_8);
        assertThat(yaml).contains(
                "config_version: 2",
                "bot:",
                "discord_ids:",
                "guild_id:",
                "main_channel_id:",
                "relay:",
                "channels:",
                "announcements:",
                "achievements:",
                "proxy_chat:",
                "embeds:",
                "death:"
        );
        assertThat(yaml).doesNotContain(
                "delivery:", "limits:", "gateway:", "diagnostics:", "scheduler:", "backend_status:",
                "auto_create:", "webhook:", "cooldown_millis:", "duplicate_window_millis:",
                "max_repeated_characters:", "duplicate_message:", "require_link_to_relay:",
                "thumbnail_url:", "image_url:", "nickname_sync:", "link_announcement:", "linking:"
        );
        assertThat(yaml).doesNotContain("\nchannel_id:", "\nlinked_role_id:", "\nlink_command_name:", "store_file:");
        assertThat(yaml).doesNotContain("token:", "chat_url:", "announcements_url:", "TENSA_DISCORD_BOT_TOKEN=");
    }

    @Test
    void environmentSecretsOverrideConfiguredValuesAndStayRedacted() {
        Tensa.pluginPath = tempDir;
        DiscordConfig config = new DiscordConfig();
        config.botToken = "configured-token";
        config.webhookUrl = "https://discord.com/api/webhooks/111/configured";
        config.guildId = "12345678901234567";
        config.channelId = "22345678901234567";

        DiscordSettings settings = config.settings(Map.of(
                DiscordSettings.TOKEN_ENV, "environment-token",
                DiscordSettings.WEBHOOK_ENV, "https://discord.com/api/webhooks/222/environment"
        ));

        assertThat(settings.credentials().botToken()).isEqualTo("environment-token");
        assertThat(settings.credentials().webhookUri()).get()
                .extracting(Object::toString)
                .isEqualTo("https://discord.com/api/webhooks/222/environment");
        assertThat(settings.toString())
                .doesNotContain(
                        "environment-token",
                        "configured-token",
                        "/api/webhooks/222/environment"
                );
    }

    @Test
    void rejectsInvalidDiscordIds() {
        Tensa.pluginPath = tempDir;
        DiscordConfig config = new DiscordConfig();
        config.botToken = "test-token";
        config.guildId = "not-an-id";
        config.channelId = "22345678901234567";

        assertThatThrownBy(() -> config.settings(Map.of()))
                .isInstanceOf(DiscordConfigurationException.class)
                .hasMessageContaining("guild_id");

    }

    @Test
    void prunesObsoleteTechnicalAndModerationSettingsFromExistingV2() throws IOException {
        Tensa.pluginPath = tempDir;
        Files.createDirectories(tempDir.resolve("communications"));
        Path file = tempDir.resolve("communications/discord.yml");
        Files.writeString(file, """
                config_version: 2
                bot:
                  enabled: false
                webhook:
                  enabled: true
                proxy_chat:
                  cooldown_millis: 1500
                  duplicate_window_millis: 15000
                  require_link_to_relay:
                    enabled: true
                announcements:
                  backend_status:
                    poll_interval_seconds: 15
                limits:
                  queue_capacity: 999
                delivery:
                  attempts: 5
                gateway:
                  max_reconnect_delay_seconds: 500
                scheduler:
                  worker_threads: 8
                """);

        new DiscordConfig().reloadCfg();

        String yaml = Files.readString(file);
        assertThat(yaml).doesNotContain(
                "webhook:", "cooldown_millis:", "duplicate_window_millis:", "require_link_to_relay:",
                "backend_status:", "limits:", "delivery:", "gateway:", "scheduler:"
        );
    }

    @Test
    void rejectsWrongScalarTypesInsteadOfSilentlyUsingDefaults() throws Exception {
        Tensa.pluginPath = tempDir;
        Files.createDirectories(tempDir.resolve("communications"));
        Files.writeString(tempDir.resolve("communications/discord.yml"), """
                config_version: 2
                bot:
                  enabled: definitely
                """);

        DiscordConfig config = new DiscordConfig();

        assertThatThrownBy(config::reloadCfg)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bot.enabled")
                .hasMessageContaining("boolean");
    }
}
