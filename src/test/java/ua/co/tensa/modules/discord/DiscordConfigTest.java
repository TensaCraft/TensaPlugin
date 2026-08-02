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
        assertThat(yaml).contains("guild_id:", "channel_id:", "link_command_name:");
        assertThat(yaml).doesNotContain("token:", "webhook:", "TENSA_DISCORD_BOT_TOKEN=");
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
    void rejectsUnsafeStorePathAndInvalidDiscordIds() {
        Tensa.pluginPath = tempDir;
        DiscordConfig config = new DiscordConfig();
        config.botToken = "test-token";
        config.guildId = "not-an-id";
        config.channelId = "22345678901234567";

        assertThatThrownBy(() -> config.settings(Map.of()))
                .isInstanceOf(DiscordConfigurationException.class)
                .hasMessageContaining("guild_id");

        config.guildId = "12345678901234567";
        config.linkStoreFile = "../outside.json";
        assertThatThrownBy(() -> config.settings(Map.of()))
                .isInstanceOf(DiscordConfigurationException.class)
                .hasMessageContaining("store_file");
    }
}
