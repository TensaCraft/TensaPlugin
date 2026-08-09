package ua.co.tensa.modules.discord;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DiscordLinkServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void durableLinkAssignsRoleOnceAndCanReconcileItOnPlayerEvents() throws Exception {
        DiscordSettings settings = DiscordTestSettings.create(tempDir);
        InMemoryDiscordLinkRepository repository = new InMemoryDiscordLinkRepository();
        repository.initialize();
        LinkGateway gateway = new LinkGateway();
        UUID playerId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        try (DiscordLinkService service = new DiscordLinkService(
                repository,
                new LinkCodeRegistry(settings.linkCodeTtl(), settings.linkCodeLength()),
                gateway,
                settings
        )) {
            DiscordLinkService.IssuedCode issued = service.issue(playerId, "Pilot", Instant.now());
            DiscordLinkService.Result result = service.complete(
                    issued.code(), "12345678901234567", "PilotDiscord").get();

            assertThat(result.type()).isEqualTo(DiscordLinkService.ResultType.LINKED);
            assertThat(repository.findByPlayer(playerId)).isPresent();
            assertThat(gateway.assignments).hasValue(1);
            service.reconcileRole(playerId);
            awaitValue(gateway.assignments, 2);
            assertThat(service.complete(issued.code(), "12345678901234567", "PilotDiscord").get().type())
                    .isEqualTo(DiscordLinkService.ResultType.INVALID_CODE);
            assertThat(gateway.assignments).hasValue(2);
        }
    }

    @Test
    void linkDoesNotPerformDiscordRoleCallsWhenNoRoleIsConfigured() throws Exception {
        DiscordSettings settings = DiscordTestSettings.create(tempDir, config -> config.linkedRoleId = "");
        InMemoryDiscordLinkRepository repository = new InMemoryDiscordLinkRepository();
        repository.initialize();
        LinkGateway gateway = new LinkGateway();
        UUID playerId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        try (DiscordLinkService service = new DiscordLinkService(
                repository,
                new LinkCodeRegistry(settings.linkCodeTtl(), settings.linkCodeLength()),
                gateway,
                settings
        )) {
            DiscordLinkService.IssuedCode issued = service.issue(playerId, "NoRole", Instant.now());
            assertThat(service.complete(issued.code(), "22345678901234567", "NoRoleDiscord").get().type())
                    .isEqualTo(DiscordLinkService.ResultType.LINKED);
            assertThat(gateway.assignments).hasValue(0);
        }
    }

    private static void awaitValue(AtomicInteger value, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (value.get() < expected && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(value).hasValue(expected);
    }

    private static final class LinkGateway implements DiscordGateway {
        private final AtomicInteger assignments = new AtomicInteger();
        @Override public void start(java.util.function.Consumer<DiscordInboundMessage> inbound, SlashLinkHandler slash, Runnable ready) { }
        @Override public CompletableFuture<Void> sendBotMessage(DiscordRoute route, String content) { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Void> sendBotEmbed(DiscordRoute route, DiscordEmbedMessage embed) { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Void> assignLinkedRole(String id) { assignments.incrementAndGet(); return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Void> removeLinkedRole(String id) { return CompletableFuture.completedFuture(null); }
        @Override public boolean isReady() { return true; }
        @Override public String selfUserId() { return "bot"; }
        @Override public void close(Duration timeout) { }
    }
}
