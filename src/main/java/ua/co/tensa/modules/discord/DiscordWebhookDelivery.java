package ua.co.tensa.modules.discord;

import java.util.concurrent.CompletableFuture;

interface DiscordWebhookDelivery extends AutoCloseable {
    boolean configured(DiscordRoute route);

    CompletableFuture<Void> send(DiscordRoute route, String content, String username, String avatarUrl);

    CompletableFuture<Void> sendEmbed(DiscordRoute route, DiscordEmbedMessage embed);

    default void invalidate(DiscordRoute route) {
    }

    @Override
    default void close() {
    }
}
