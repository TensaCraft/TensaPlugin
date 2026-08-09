package ua.co.tensa.modules.discord;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.co.tensa.core.storage.CoreStorageService;
import ua.co.tensa.modules.runtime.ModuleScheduler;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class DiscordWebhookProvisionerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void fetchesOrCreatesEachMissingRouteAndPersistsOnlyBindingMetadata() throws Exception {
        DiscordSettings settings = DiscordTestSettings.create(temporaryDirectory, config -> {
            config.webhookAutoCreate = true;
            config.webhookUrl = "";
            config.eventsWebhookUrl = "";
            ((java.util.Map<String, Object>) config.embeds.get("join")).put("enabled", true);
        });
        FakeGateway gateway = new FakeGateway();
        DiscordWebhookClient client = new DiscordWebhookClient(settings);
        try (CoreStorageService storage = CoreStorageService.local(temporaryDirectory.resolve("provision"), "test_");
             ModuleScheduler scheduler = scheduler();
             DiscordWebhookBindingRepository repository = new DiscordWebhookBindingRepository(storage);
             DiscordWebhookProvisioner provisioner = new DiscordWebhookProvisioner(
                     settings, gateway, client, repository, scheduler, new CommunicationsMetrics())) {
            repository.initialize();

            provisioner.refreshAll();

            await(() -> repository.find(DiscordRoute.CHAT).isPresent()
                    && repository.find(DiscordRoute.EVENTS).isPresent());
            assertThat(client.configured(DiscordRoute.CHAT)).isTrue();
            assertThat(client.configured(DiscordRoute.EVENTS)).isTrue();
            assertThat(gateway.provisions).hasValue(2);
        }
    }

    @Test
    void invalidationCoalescesRecoveryAndRotatesBinding() throws Exception {
        DiscordSettings settings = DiscordTestSettings.create(temporaryDirectory, config -> {
            config.webhookAutoCreate = true;
            config.webhookUrl = "";
            config.eventsWebhookUrl = "https://discord.com/api/webhooks/33333333333333333/static-events";
        });
        FakeGateway gateway = new FakeGateway();
        DiscordWebhookClient client = new DiscordWebhookClient(settings);
        try (CoreStorageService storage = CoreStorageService.local(temporaryDirectory.resolve("rotate"), "test_");
             ModuleScheduler scheduler = scheduler();
             DiscordWebhookBindingRepository repository = new DiscordWebhookBindingRepository(storage);
             DiscordWebhookProvisioner provisioner = new DiscordWebhookProvisioner(
                     settings, gateway, client, repository, scheduler, new CommunicationsMetrics())) {
            repository.initialize();
            provisioner.refreshAll();
            await(() -> gateway.provisions.get() == 1);
            String first = repository.find(DiscordRoute.CHAT).orElseThrow().webhookId();

            client.invalidate(DiscordRoute.CHAT);
            client.invalidate(DiscordRoute.CHAT);

            await(() -> gateway.provisions.get() == 2
                    && !repository.find(DiscordRoute.CHAT).orElseThrow().webhookId().equals(first));
            assertThat(repository.find(DiscordRoute.CHAT).orElseThrow().webhookId()).isNotEqualTo(first);
            assertThat(gateway.provisions).hasValue(2);
        }
    }

    private static ModuleScheduler scheduler() {
        return new ModuleScheduler("webhook-test", new ModuleScheduler.Defaults(
                8, 1, 8, Duration.ofSeconds(2), 1,
                Duration.ofMillis(1), Duration.ofMillis(10), 0.0
        ));
    }

    private static void await(CheckedCondition condition) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (!condition.test() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(condition.test()).isTrue();
    }

    @FunctionalInterface
    private interface CheckedCondition {
        boolean test() throws Exception;
    }

    private static final class FakeGateway implements DiscordGateway {
        private final AtomicInteger provisions = new AtomicInteger();

        @Override
        public CompletableFuture<ManagedDiscordWebhook> ensureManagedWebhook(
                DiscordRoute route, String name, String preferredWebhookId) {
            int sequence = provisions.incrementAndGet();
            String id = "1234567890123456" + sequence;
            return CompletableFuture.completedFuture(ManagedDiscordWebhook.from(
                    route, id, URI.create("https://discord.com/api/webhooks/" + id + "/runtime-token-" + sequence)));
        }

        @Override public void start(Consumer<DiscordInboundMessage> inbound, SlashLinkHandler slash, Runnable ready) { }
        @Override public CompletableFuture<Void> sendBotMessage(DiscordRoute route, String content) { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Void> sendBotEmbed(DiscordRoute route, DiscordEmbedMessage embed) { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Void> assignLinkedRole(String id) { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Void> removeLinkedRole(String id) { return CompletableFuture.completedFuture(null); }
        @Override public boolean isReady() { return true; }
        @Override public String selfUserId() { return "bot"; }
        @Override public void close(Duration timeout) { }
    }
}
