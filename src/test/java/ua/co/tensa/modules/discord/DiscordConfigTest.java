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

        String yaml = Files.readString(tempDir.resolve("discord.yml"), StandardCharsets.UTF_8);
        assertThat(yaml).contains(
                "config_version: 2",
                "bot:",
                "webhook:",
                "discord_ids:",
                "guild_id:",
                "main_channel_id:",
                "relay:",
                "announcements:",
                "achievements:",
                "linking:",
                "proxy_chat:",
                "embeds:",
                "delivery:",
                "limits:",
                "gateway:",
                "diagnostics:"
        );
        assertThat(yaml).doesNotContain("\nchannel_id:", "\nlinked_role_id:", "\nlink_command_name:", "store_file:");
        assertThat(yaml).contains("https://mc-heads.net/avatar/{player}/128");
        assertThat(yaml).doesNotContain("token:", "chat_url:", "announcements_url:", "TENSA_DISCORD_BOT_TOKEN=");
    }

    @Test
    void environmentSecretsOverrideConfiguredValuesAndStayRedacted() {
        Tensa.pluginPath = tempDir;
        DiscordConfig config = new DiscordConfig();
        config.botToken = "configured-token";
        config.webhookUrl = "https://discord.com/api/webhooks/111/configured";
        config.eventsWebhookUrl = "https://discord.com/api/webhooks/333/configured-events";
        config.guildId = "12345678901234567";
        config.channelId = "22345678901234567";

        DiscordSettings settings = config.settings(Map.of(
                DiscordSettings.TOKEN_ENV, "environment-token",
                DiscordSettings.WEBHOOK_ENV, "https://discord.com/api/webhooks/222/environment",
                DiscordSettings.EVENTS_WEBHOOK_ENV, "https://discord.com/api/webhooks/444/environment-events"
        ));

        assertThat(settings.credentials().botToken()).isEqualTo("environment-token");
        assertThat(settings.credentials().webhookUri()).get()
                .extracting(Object::toString)
                .isEqualTo("https://discord.com/api/webhooks/222/environment");
        assertThat(settings.credentials().eventsWebhookUri()).get()
                .extracting(Object::toString)
                .isEqualTo("https://discord.com/api/webhooks/444/environment-events");
        assertThat(settings.toString())
                .doesNotContain(
                        "environment-token",
                        "configured-token",
                        "/api/webhooks/222/environment",
                        "/api/webhooks/444/environment-events"
                );
    }

    @Test
    void rejectsInvalidDiscordIdsAndLinkBounds() {
        Tensa.pluginPath = tempDir;
        DiscordConfig config = new DiscordConfig();
        config.botToken = "test-token";
        config.guildId = "not-an-id";
        config.channelId = "22345678901234567";

        assertThatThrownBy(() -> config.settings(Map.of()))
                .isInstanceOf(DiscordConfigurationException.class)
                .hasMessageContaining("guild_id");

        config.guildId = "12345678901234567";
        config.maxLinks = 0;
        assertThatThrownBy(() -> config.settings(Map.of()))
                .isInstanceOf(DiscordConfigurationException.class)
                .hasMessageContaining("max_links");
    }

    @Test
    void rejectsWrongScalarTypesInsteadOfSilentlyUsingDefaults() throws Exception {
        Tensa.pluginPath = tempDir;
        Files.writeString(tempDir.resolve("discord.yml"), """
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
