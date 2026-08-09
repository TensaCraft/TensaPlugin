package ua.co.tensa.modules.discord;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

interface DiscordGateway extends AutoCloseable {
    void start(
            Consumer<DiscordInboundMessage> inboundHandler,
            SlashLinkHandler slashLinkHandler,
            Runnable readyHandler
    );

    CompletableFuture<Void> sendBotMessage(DiscordRoute route, String content);

    CompletableFuture<Void> sendBotEmbed(DiscordRoute route, DiscordEmbedMessage embed);

    CompletableFuture<Void> assignLinkedRole(String discordUserId);

    CompletableFuture<Void> removeLinkedRole(String discordUserId);

    default CompletableFuture<Void> updateNickname(String discordUserId, String nickname) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Discord nickname sync is unavailable"));
    }

    default CompletableFuture<Void> sendTemporaryReply(
            String channelId,
            String messageId,
            String content,
            Duration deleteAfter
    ) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Discord temporary replies are unavailable"));
    }

    default CompletableFuture<ManagedDiscordWebhook> ensureManagedWebhook(
            DiscordRoute route,
            String name,
            String preferredWebhookId
    ) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Discord webhook provisioning is unavailable"));
    }

    boolean isReady();

    default boolean isReady(DiscordRoute route) {
        return isReady();
    }

    String selfUserId();

    default String runtimeState() {
        return isReady() ? "ready" : "unavailable";
    }

    default String slashState() {
        return isReady() ? "unknown" : "unavailable";
    }

    default long reconnectCount() {
        return 0L;
    }

    void close(Duration timeout);

    @Override
    default void close() {
        close(Duration.ofSeconds(5));
    }

    @FunctionalInterface
    interface SlashLinkHandler {
        CompletableFuture<DiscordEmbedMessage> link(String code, String discordUserId, String discordUserName);
    }
}
