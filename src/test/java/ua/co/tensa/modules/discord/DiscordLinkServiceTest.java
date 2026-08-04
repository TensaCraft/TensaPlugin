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
    void durableLinkIsNotRolledBackWhenPostLinkPipelineRejectsItsEffect() throws Exception {
        DiscordSettings settings = DiscordTestSettings.create(tempDir);
        InMemoryDiscordLinkRepository repository = new InMemoryDiscordLinkRepository();
        repository.initialize();
        AtomicInteger effects = new AtomicInteger();
        UUID playerId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        try (DiscordLinkService service = new DiscordLinkService(
                repository,
                new LinkCodeRegistry(settings.linkCodeTtl(), settings.linkCodeLength()),
                new LinkGateway(),
                settings,
                ignored -> {
                    effects.incrementAndGet();
                    throw new IllegalStateException("queue rejected");
                }
        )) {
            DiscordLinkService.IssuedCode issued = service.issue(playerId, "Pilot", Instant.now());
            DiscordLinkService.Result result = service.complete(
                    issued.code(), "12345678901234567", "PilotDiscord").get();

            assertThat(result.type()).isEqualTo(DiscordLinkService.ResultType.LINKED);
            assertThat(repository.findByPlayer(playerId)).isPresent();
            assertThat(effects).hasValue(1);
            assertThat(service.complete(issued.code(), "12345678901234567", "PilotDiscord").get().type())
                    .isEqualTo(DiscordLinkService.ResultType.INVALID_CODE);
            assertThat(effects).hasValue(1);
        }
    }

    private static final class LinkGateway implements DiscordGateway {
        @Override public void start(java.util.function.Consumer<DiscordInboundMessage> inbound, SlashLinkHandler slash, Runnable ready) { }
        @Override public CompletableFuture<Void> sendBotMessage(DiscordRoute route, String content) { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Void> sendBotEmbed(DiscordRoute route, DiscordEmbedMessage embed) { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Void> assignLinkedRole(String id) { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Void> removeLinkedRole(String id) { return CompletableFuture.completedFuture(null); }
        @Override public boolean isReady() { return true; }
        @Override public String selfUserId() { return "bot"; }
        @Override public void close(Duration timeout) { }
    }
}
