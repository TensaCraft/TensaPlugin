package ua.co.tensa.modules.discord;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.co.tensa.core.storage.CoreStorageService;

import java.net.URI;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DiscordWebhookBindingRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsOnlySafeBindingMetadata() {
        try (CoreStorageService storage = CoreStorageService.local(temporaryDirectory.resolve("webhook"), "test_");
             DiscordWebhookBindingRepository repository = new DiscordWebhookBindingRepository(storage)) {
            repository.initialize();
            ManagedDiscordWebhook webhook = ManagedDiscordWebhook.from(
                    DiscordRoute.CHAT,
                    "12345678901234567",
                    URI.create("https://discord.com/api/webhooks/12345678901234567/super-secret-token")
            );

            repository.remember(webhook);

            assertThat(repository.find(DiscordRoute.CHAT)).get()
                    .extracting(DiscordWebhookBinding::webhookId, DiscordWebhookBinding::tokenFingerprint)
                    .containsExactly("12345678901234567", webhook.tokenFingerprint());
            assertThat(repository.find(DiscordRoute.CHAT).orElseThrow().toString())
                    .doesNotContain("super-secret-token", "discord.com");
        }
    }

    @Test
    void replacingAndClearingBindingIsIdempotent() {
        try (CoreStorageService storage = CoreStorageService.local(temporaryDirectory.resolve("replace"), "test_");
             DiscordWebhookBindingRepository repository = new DiscordWebhookBindingRepository(storage)) {
            repository.initialize();
            repository.remember(ManagedDiscordWebhook.from(
                    DiscordRoute.EVENTS,
                    "12345678901234567",
                    URI.create("https://discord.com/api/webhooks/12345678901234567/first-token")
            ));
            ManagedDiscordWebhook replacement = ManagedDiscordWebhook.from(
                    DiscordRoute.EVENTS,
                    "22345678901234567",
                    URI.create("https://discord.com/api/webhooks/22345678901234567/second-token")
            );

            repository.remember(replacement);
            repository.clear(DiscordRoute.EVENTS);
            repository.clear(DiscordRoute.EVENTS);

            assertThat(repository.find(DiscordRoute.EVENTS)).isEmpty();
        }
    }
}
