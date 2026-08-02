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
    void definiteWebhookRejectionUsesBotFallbackOnce() {
        FakeGateway gateway = new FakeGateway();
        DiscordDelivery delivery = new DiscordDelivery(
                gateway,
                new FailedWebhook(new DiscordWebhookClient.RejectedResponseException(400))
        );

        delivery.send(message()).join();

        assertThat(gateway.botMessages.get()).isOne();
    }

    private static DiscordOutboundMessage message() {
        return DiscordOutboundMessage.chat("webhook", "bot", "player", "");
    }

    private record FailedWebhook(Throwable failure) implements DiscordWebhookDelivery {
        @Override public boolean configured(DiscordRoute route) { return true; }
        @Override public CompletableFuture<Void> send(DiscordRoute route, String content, String username, String avatarUrl) {
            return CompletableFuture.failedFuture(failure);
        }
        @Override public CompletableFuture<Void> sendEmbed(DiscordRoute route, DiscordEmbedMessage embed) {
            return CompletableFuture.failedFuture(failure);
        }
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
