package ua.co.tensa.modules.discord;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscordDeliveryTest {
    @Test
    void ambiguousWebhookFailureDoesNotSendDuplicateBotFallback() {
        FakeGateway gateway = new FakeGateway();
        DiscordDelivery delivery = new DiscordDelivery(gateway, new FailedWebhook(new IOException("timeout")));

        assertThatThrownBy(() -> delivery.send(message()).join())
                .hasRootCauseInstanceOf(IOException.class);
        assertThat(gateway.botMessages.get()).isZero();
    }

    @Test
    void invalidWebhookPayloadNeverLosesPlayerIdentityThroughBotFallback() {
        FakeGateway gateway = new FakeGateway();
        DiscordDelivery delivery = new DiscordDelivery(
                gateway,
                new FailedWebhook(new DiscordWebhookClient.RejectedResponseException(400))
        );

        assertThatThrownBy(() -> delivery.send(message()).join())
                .hasRootCauseInstanceOf(DiscordWebhookClient.RejectedResponseException.class);
        assertThat(gateway.botMessages.get()).isZero();
    }

    @Test
    void serverAndRateLimitResponsesNeverUseCrossTransportFallback() {
        FakeGateway gateway = new FakeGateway();
        DiscordDelivery serverFailure = new DiscordDelivery(
                gateway,
                new FailedWebhook(new DiscordWebhookClient.RejectedResponseException(503))
        );

        assertThatThrownBy(() -> serverFailure.send(message()).join())
                .hasRootCauseInstanceOf(DiscordWebhookClient.RejectedResponseException.class);
        assertThat(gateway.botMessages).hasValue(0);
        assertThat(DiscordDelivery.retryable(new DiscordWebhookClient.RejectedResponseException(503))).isTrue();
        assertThat(DiscordDelivery.retryable(new DiscordWebhookClient.RejectedResponseException(429))).isTrue();
        assertThat(DiscordDelivery.retryable(new DiscordWebhookClient.RejectedResponseException(403))).isFalse();
        assertThat(DiscordDelivery.retryable(new IOException("ambiguous"))).isFalse();
    }

    @Test
    void revokedManagedWebhookIsInvalidatedWithoutCrossTransportFallback() {
        FakeGateway gateway = new FakeGateway();
        FailedWebhook webhook = new FailedWebhook(new DiscordWebhookClient.RejectedResponseException(404));
        DiscordDelivery delivery = new DiscordDelivery(gateway, webhook);

        assertThatThrownBy(() -> delivery.send(message()).join())
                .hasRootCauseInstanceOf(DiscordWebhookClient.RejectedResponseException.class);

        assertThat(webhook.invalidations).hasValue(1);
        assertThat(gateway.botMessages).hasValue(0);
        assertThat(DiscordDelivery.retryable(new DiscordWebhookClient.RejectedResponseException(404))).isTrue();
    }

    @Test
    void missingChatWebhookIsUnavailableInsteadOfFallingBackToBotIdentity() {
        FakeGateway gateway = new FakeGateway();
        DiscordWebhookDelivery missing = new DiscordWebhookDelivery() {
            @Override public boolean configured(DiscordRoute route) { return false; }
            @Override public CompletableFuture<Void> send(DiscordRoute route, String content, String username, String avatarUrl) {
                return CompletableFuture.failedFuture(new AssertionError("must not send"));
            }
            @Override public CompletableFuture<Void> sendEmbed(DiscordRoute route, DiscordEmbedMessage embed) {
                return CompletableFuture.failedFuture(new AssertionError("must not send"));
            }
        };
        DiscordDelivery delivery = new DiscordDelivery(gateway, missing);

        assertThat(delivery.ready(message())).isFalse();
        assertThatThrownBy(() -> delivery.send(message()).join())
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("webhook");
        assertThat(gateway.botMessages).hasValue(0);
    }

    private static DiscordOutboundMessage message() {
        return DiscordOutboundMessage.chat("webhook", "player", "");
    }

    private static final class FailedWebhook implements DiscordWebhookDelivery {
        private final Throwable failure;
        private final AtomicInteger invalidations = new AtomicInteger();

        private FailedWebhook(Throwable failure) {
            this.failure = failure;
        }

        @Override public boolean configured(DiscordRoute route) { return true; }
        @Override public CompletableFuture<Void> send(DiscordRoute route, String content, String username, String avatarUrl) {
            return CompletableFuture.failedFuture(failure);
        }
        @Override public CompletableFuture<Void> sendEmbed(DiscordRoute route, DiscordEmbedMessage embed) {
            return CompletableFuture.failedFuture(failure);
        }
        @Override public void invalidate(DiscordRoute route) { invalidations.incrementAndGet(); }
    }

    private static final class FakeGateway implements DiscordGateway {
        private final AtomicInteger botMessages = new AtomicInteger();
        @Override public void start(Consumer<DiscordInboundMessage> inbound, SlashLinkHandler slash, Runnable ready) { }
        @Override public CompletableFuture<Void> sendBotMessage(DiscordRoute route, String content) {
            botMessages.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        }
        @Override public CompletableFuture<Void> sendBotEmbed(DiscordRoute route, DiscordEmbedMessage embed) {
            botMessages.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        }
        @Override public CompletableFuture<Void> assignLinkedRole(String id) { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Void> removeLinkedRole(String id) { return CompletableFuture.completedFuture(null); }
        @Override public boolean isReady() { return true; }
        @Override public String selfUserId() { return "bot"; }
        @Override public void close(Duration timeout) { }
    }
}
