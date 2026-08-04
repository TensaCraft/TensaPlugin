package ua.co.tensa.modules.discord;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PostLinkEffectsTest {
    private static final LinkedAccount ACCOUNT = new LinkedAccount(
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
            "Pilot",
            "12345678901234567",
            "DiscordPilot",
            Instant.EPOCH
    );

    @Test
    void announcesExactlyOnceAndRetriesOnlyTransientNicknameFailures() throws Exception {
        EffectsGateway gateway = new EffectsGateway();
        gateway.failures.set(1);
        AtomicInteger announcements = new AtomicInteger();
        CommunicationsMetrics metrics = new CommunicationsMetrics();
        try (PostLinkEffects effects = new PostLinkEffects(
                gateway,
                ignored -> announcements.incrementAndGet(),
                true,
                8,
                3,
                Duration.ofMillis(1),
                metrics
        )) {
            assertThat(effects.submit(ACCOUNT)).isTrue();
            assertThat(gateway.completed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(announcements).hasValue(1);
            assertThat(gateway.nicknameAttempts).hasValue(2);
            assertThat(metrics.snapshot("", "", "", "", 0, 0, 0, 0).retries()).isEqualTo(1);
        }
    }

    @Test
    void permanentNicknameFailureDoesNotRetryAnnouncementOrUndoCompletedWork() throws Exception {
        EffectsGateway gateway = new EffectsGateway();
        gateway.permanentFailure = true;
        AtomicInteger announcements = new AtomicInteger();
        try (PostLinkEffects effects = new PostLinkEffects(
                gateway,
                ignored -> announcements.incrementAndGet(),
                true,
                8,
                3,
                Duration.ofMillis(1),
                new CommunicationsMetrics()
        )) {
            assertThat(effects.submit(ACCOUNT)).isTrue();
            assertThat(gateway.completed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(announcements).hasValue(1);
            assertThat(gateway.nicknameAttempts).hasValue(1);
        }
    }

    @Test
    void rejectsOverflowWithoutUnboundingTheWorkerQueue() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        try (PostLinkEffects effects = new PostLinkEffects(
                new EffectsGateway(),
                ignored -> {
                    try {
                        release.await();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                },
                false,
                1,
                1,
                Duration.ofMillis(1),
                new CommunicationsMetrics()
        )) {
            assertThat(effects.submit(ACCOUNT)).isTrue();
            assertThat(effects.submit(ACCOUNT)).isTrue();
            assertThat(effects.submit(ACCOUNT)).isFalse();
            release.countDown();
        }
    }

    private static final class EffectsGateway implements DiscordGateway {
        private final AtomicInteger nicknameAttempts = new AtomicInteger();
        private final AtomicInteger failures = new AtomicInteger();
        private final CountDownLatch completed = new CountDownLatch(1);
        private volatile boolean permanentFailure;

        @Override public void start(java.util.function.Consumer<DiscordInboundMessage> inbound, SlashLinkHandler slash, Runnable ready) { }
        @Override public CompletableFuture<Void> sendBotMessage(DiscordRoute route, String content) { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Void> sendBotEmbed(DiscordRoute route, DiscordEmbedMessage embed) { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Void> assignLinkedRole(String discordUserId) { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Void> removeLinkedRole(String discordUserId) { return CompletableFuture.completedFuture(null); }
        @Override public boolean isReady() { return true; }
        @Override public String selfUserId() { return ""; }
        @Override public void close(Duration timeout) { }

        @Override
        public CompletableFuture<Void> updateNickname(String discordUserId, String nickname) {
            nicknameAttempts.incrementAndGet();
            if (permanentFailure) {
                completed.countDown();
                return CompletableFuture.failedFuture(new IllegalStateException("missing permission"));
            }
            if (failures.getAndUpdate(value -> Math.max(0, value - 1)) > 0) {
                return CompletableFuture.failedFuture(new IOException("temporary"));
            }
            completed.countDown();
            return CompletableFuture.completedFuture(null);
        }
    }
}
