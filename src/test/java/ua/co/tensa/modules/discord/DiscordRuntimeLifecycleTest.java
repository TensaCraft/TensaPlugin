package ua.co.tensa.modules.discord;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.co.tensa.modules.chat.ProxyChatMessage;

import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class DiscordRuntimeLifecycleTest {
    @TempDir
    Path tempDir;

    @Test
    void startsRelaysOnlyMinecraftGlobalMessagesAndClosesAllWorkers() throws Exception {
        DiscordSettings settings = DiscordTestSettings.create(tempDir, config -> config.joinMessages = true);
        AtomicLinkStore store = new AtomicLinkStore(tempDir, settings.linkStorePath());
        store.load();
        FakeGateway gateway = new FakeGateway();
        DiscordLinkService links = new DiscordLinkService(
                store,
                new LinkCodeRegistry(settings.linkCodeTtl(), settings.linkCodeLength()),
                gateway,
                settings
        );
        DiscordRuntime runtime = new DiscordRuntime(
                settings,
                gateway,
                links,
                new DiscordDelivery(gateway, new DiscordWebhookClient(settings))
        );

        runtime.start();
        assertThat(runtime.isRunning()).isTrue();
        assertThat(runtime.relayMinecraftChat(message(ProxyChatMessage.Origin.DISCORD, "global"))).isFalse();
        assertThat(runtime.relayMinecraftChat(message(ProxyChatMessage.Origin.MINECRAFT, "staff"))).isFalse();
        assertThat(runtime.relayMinecraftChat(message(ProxyChatMessage.Origin.MINECRAFT, "global"))).isTrue();
        awaitCount(gateway.sentMessages, 1);
        runtime.announceJoin("Pilot", "Aero");
        awaitCount(gateway.sentMessages, 2);
        assertThat(gateway.routes).containsExactly(DiscordRoute.CHAT, DiscordRoute.EVENTS);

        runtime.close();

        assertThat(runtime.isRunning()).isFalse();
        assertThat(gateway.closed).isTrue();
        assertThat(runtime.relayMinecraftChat(message(ProxyChatMessage.Origin.MINECRAFT, "global"))).isFalse();
    }

    private static ProxyChatMessage message(ProxyChatMessage.Origin origin, String channel) {
        return new ProxyChatMessage(
                origin,
                channel,
                "Aero",
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                "Pilot",
                "Hello @everyone **world**"
        );
    }

    private static void awaitCount(AtomicInteger count, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (count.get() < expected && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
        assertThat(count.get()).isEqualTo(expected);
    }

    private static final class FakeGateway implements DiscordGateway {
        private final AtomicInteger sentMessages = new AtomicInteger();
        private final ConcurrentLinkedQueue<DiscordRoute> routes = new ConcurrentLinkedQueue<>();
        private volatile boolean ready;
        private volatile boolean closed;

        @Override
        public void start(
                Consumer<DiscordInboundMessage> inboundHandler,
                SlashLinkHandler slashLinkHandler,
                Runnable readyHandler
        ) {
            ready = true;
            readyHandler.run();
        }

        @Override
        public CompletableFuture<Void> sendBotMessage(DiscordRoute route, String content) {
            routes.add(route);
            sentMessages.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> assignLinkedRole(String discordUserId) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> removeLinkedRole(String discordUserId) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public boolean isReady() {
            return ready;
        }

        @Override
        public String selfUserId() {
            return "99999999999999999";
        }

        @Override
        public void close(Duration timeout) {
            ready = false;
            closed = true;
        }
    }
}
