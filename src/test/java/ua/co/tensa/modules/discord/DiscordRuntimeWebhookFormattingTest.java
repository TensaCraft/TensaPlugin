package ua.co.tensa.modules.discord;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.co.tensa.modules.chat.ProxyChatMessage;

import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class DiscordRuntimeWebhookFormattingTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void minecraftChatPrefersWebhookIdentityAvatarAndFormattedContent() throws Exception {
        DiscordSettings settings = DiscordTestSettings.create(temporaryDirectory, config ->
                config.minecraftToDiscordFormat = "[{server}] {message}");
        FakeGateway gateway = new FakeGateway();
        CapturingWebhook webhook = new CapturingWebhook();
        DiscordLinkRepository repository = new InMemoryDiscordLinkRepository();
        repository.initialize();
        DiscordLinkService links = new DiscordLinkService(
                repository,
                new LinkCodeRegistry(settings.linkCodeTtl(), settings.linkCodeLength()),
                gateway,
                settings
        );
        try (DiscordRuntime runtime = new DiscordRuntime(
                settings, gateway, links, new DiscordDelivery(gateway, webhook))) {
            runtime.start();

            runtime.relayMinecraftChat(new ProxyChatMessage(
                    ProxyChatMessage.Origin.MINECRAFT,
                    "global",
                    "Aeronautics",
                    UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                    "Pilot One",
                    "hello"
            ));

            assertThat(webhook.sent.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(webhook.content).isEqualTo("[Aeronautics] hello");
            assertThat(webhook.username).isEqualTo("Pilot One");
            assertThat(webhook.avatarUrl).isEqualTo("https://mc-heads.net/avatar/Pilot+One/128");
            assertThat(gateway.botMessages).isZero();
        }
    }

    private static final class CapturingWebhook implements DiscordWebhookDelivery {
        private final CountDownLatch sent = new CountDownLatch(1);
        private volatile String content;
        private volatile String username;
        private volatile String avatarUrl;

        @Override public boolean configured(DiscordRoute route) { return true; }
        @Override public CompletableFuture<Void> send(DiscordRoute route, String content, String username, String avatarUrl) {
            this.content = content;
            this.username = username;
            this.avatarUrl = avatarUrl;
            sent.countDown();
            return CompletableFuture.completedFuture(null);
        }
        @Override public CompletableFuture<Void> sendEmbed(DiscordRoute route, DiscordEmbedMessage embed) {
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class FakeGateway implements DiscordGateway {
        private int botMessages;
        @Override public void start(Consumer<DiscordInboundMessage> inbound, SlashLinkHandler slash, Runnable ready) { ready.run(); }
        @Override public CompletableFuture<Void> sendBotMessage(DiscordRoute route, String content) { botMessages++; return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Void> sendBotEmbed(DiscordRoute route, DiscordEmbedMessage embed) { botMessages++; return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Void> assignLinkedRole(String id) { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Void> removeLinkedRole(String id) { return CompletableFuture.completedFuture(null); }
        @Override public boolean isReady() { return true; }
        @Override public String selfUserId() { return "bot"; }
        @Override public void close(Duration timeout) { }
    }
}
