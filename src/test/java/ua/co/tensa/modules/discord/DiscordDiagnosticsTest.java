package ua.co.tensa.modules.discord;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiscordDiagnosticsTest {
    @Test
    void failureDescriptionsKeepTheCauseButRedactDiscordCredentials() {
        String token = "A".repeat(24) + "." + "b".repeat(6) + "." + "c".repeat(30);
        String webhook = "https://discord.com/api/" + "webhooks/123456789/secret-webhook-value";

        String description = DiscordDiagnostics.describe(
                new IllegalStateException("registration failed guild=123456789012345678 token=" + token
                        + " webhook=" + webhook)
        );

        assertThat(description)
                .contains("IllegalStateException", "registration failed", "[REDACTED_TOKEN]", "[REDACTED_WEBHOOK]",
                        "[REDACTED_DISCORD_ID]")
                .doesNotContain(token, webhook, "secret-webhook-value", "123456789012345678");
    }
}
