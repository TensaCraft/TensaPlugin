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

    boolean isReady();

    default boolean isReady(DiscordRoute route) {
        return isReady();
    }

    String selfUserId();

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
