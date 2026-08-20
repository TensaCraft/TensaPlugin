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

    default CompletableFuture<NicknameSyncResult> syncNickname(String discordUserId, String nickname) {
        return CompletableFuture.completedFuture(NicknameSyncResult.UNSUPPORTED);
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

    enum NicknameSyncResult {
        SUCCESS,
        UNSUPPORTED,
        MISSING_PERMISSION,
        MEMBER_NOT_FOUND,
        GUILD_UNAVAILABLE,
        INVALID_NAME,
        RATE_LIMITED,
        TRANSIENT_FAILURE,
        FAILED;

        boolean succeeded() {
            return this == SUCCESS || this == UNSUPPORTED;
        }
    }
}
