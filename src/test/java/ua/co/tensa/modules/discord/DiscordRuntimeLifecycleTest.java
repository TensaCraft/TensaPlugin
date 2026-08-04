package ua.co.tensa.modules.discord;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.co.tensa.modules.chat.ProxyChatMessage;
import ua.co.tensa.modules.chat.ProxyChatRelay;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
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
        DiscordSettings settings = DiscordTestSettings.create(tempDir, DiscordRuntimeLifecycleTest::enableJoinEmbed);
        DiscordLinkRepository store = new InMemoryDiscordLinkRepository();
        store.initialize();
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
        assertThat(runtime.relayMinecraftChat(message(ProxyChatMessage.Origin.DISCORD, "global")))
                .isEqualTo(ProxyChatRelay.Result.DISABLED);
        assertThat(runtime.relayMinecraftChat(message(ProxyChatMessage.Origin.MINECRAFT, "staff")))
                .isEqualTo(ProxyChatRelay.Result.DISABLED);
        assertThat(runtime.relayMinecraftChat(message(ProxyChatMessage.Origin.MINECRAFT, "global")))
                .isEqualTo(ProxyChatRelay.Result.ACCEPTED);
        awaitCount(gateway.sentMessages, 1);
        runtime.announceJoin("Pilot", "Aero");
        awaitCount(gateway.sentMessages, 2);
        assertThat(gateway.routes).containsExactly(DiscordRoute.CHAT, DiscordRoute.EVENTS);
        assertThat(gateway.embeds)
                .singleElement()
                .satisfies(embed -> {
                    assertThat(embed.title()).isEqualTo("Гравець приєднався");
                    assertThat(embed.color()).isEqualTo(DiscordEmbedMessage.GREEN);
                    assertThat(embed.description()).contains("Pilot");
                });

        runtime.close();

        assertThat(runtime.isRunning()).isFalse();
        assertThat(gateway.closed).isTrue();
        assertThat(runtime.relayMinecraftChat(message(ProxyChatMessage.Origin.MINECRAFT, "global")))
                .isEqualTo(ProxyChatRelay.Result.DISABLED);
    }

    @Test
    void linkGuardBlocksOnlyRelayInBothDirectionsAndRateLimitsDiscordFeedback() throws Exception {
        DiscordSettings settings = DiscordTestSettings.create(tempDir, config -> {
            LinkedHashMap<String, Object> guard = new LinkedHashMap<>();
            guard.put("enabled", true);
            guard.put("directions", List.of("minecraft_to_discord", "discord_to_minecraft"));
            guard.put("mode", "only");
            guard.put("channels", List.of("global"));
            guard.put("feedback_cooldown_seconds", 30);
            guard.put("reply_delete_after_seconds", 10);
            config.proxyChat.put("require_link_to_relay", guard);
        });
        InMemoryDiscordLinkRepository store = new InMemoryDiscordLinkRepository();
        store.initialize();
        FakeGateway gateway = new FakeGateway();
        DiscordLinkService links = new DiscordLinkService(
                store,
                new LinkCodeRegistry(settings.linkCodeTtl(), settings.linkCodeLength()),
                gateway,
                settings
        );
        AtomicInteger publishedToMinecraft = new AtomicInteger();
        DiscordRuntime runtime = new DiscordRuntime(
                settings,
                gateway,
                links,
                new DiscordDelivery(gateway, new DiscordWebhookClient(settings)),
                (source, author, content) -> publishedToMinecraft.incrementAndGet()
        );
        runtime.start();

        ProxyChatMessage minecraft = message(ProxyChatMessage.Origin.MINECRAFT, "global");
        assertThat(runtime.relayMinecraftChat(minecraft)).isEqualTo(ProxyChatRelay.Result.LINK_REQUIRED);
        store.link(new LinkedAccount(
                minecraft.playerUuid(), minecraft.playerName(), "42345678901234567", "Pilot", Instant.now()));
        assertThat(runtime.relayMinecraftChat(minecraft)).isEqualTo(ProxyChatRelay.Result.ACCEPTED);

        DiscordInboundMessage unlinked = new DiscordInboundMessage(
                settings.guildId(), settings.channelId(), "52345678901234567", "Guest", "hello", false, false, "1");
        gateway.inbound.accept(unlinked);
        gateway.inbound.accept(unlinked);
        assertThat(gateway.temporaryReplies).hasValue(1);
        assertThat(gateway.lastDeleteAfter).isEqualTo(Duration.ofSeconds(10));
        assertThat(publishedToMinecraft).hasValue(0);

        store.link(new LinkedAccount(
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                "Linked", unlinked.authorId(), "Guest", Instant.now()));
        gateway.inbound.accept(unlinked);
        awaitCount(publishedToMinecraft, 1);

        gateway.inbound.accept(new DiscordInboundMessage(
                settings.guildId(), settings.channelId(), gateway.selfUserId(), "Bot", "echo", true, false, "2"));
        assertThat(publishedToMinecraft).hasValue(1);
        runtime.close();
        assertThat(gateway.closed).isTrue();
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

    @SuppressWarnings("unchecked")
    private static void enableJoinEmbed(ua.co.tensa.modules.discord.data.DiscordConfig config) {
        ((java.util.Map<String, Object>) config.embeds.get("join")).put("enabled", true);
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
        private final ConcurrentLinkedQueue<DiscordEmbedMessage> embeds = new ConcurrentLinkedQueue<>();
        private volatile boolean ready;
        private volatile boolean closed;
        private volatile Consumer<DiscordInboundMessage> inbound = ignored -> { };
        private final AtomicInteger temporaryReplies = new AtomicInteger();
        private volatile Duration lastDeleteAfter;

        @Override
        public void start(
                Consumer<DiscordInboundMessage> inboundHandler,
                SlashLinkHandler slashLinkHandler,
                Runnable readyHandler
        ) {
            inbound = inboundHandler;
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
        public CompletableFuture<Void> sendBotEmbed(DiscordRoute route, DiscordEmbedMessage embed) {
            routes.add(route);
            embeds.add(embed);
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
        public CompletableFuture<Void> sendTemporaryReply(
                String channelId,
                String messageId,
                String content,
                Duration deleteAfter
        ) {
            temporaryReplies.incrementAndGet();
            lastDeleteAfter = deleteAfter;
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
