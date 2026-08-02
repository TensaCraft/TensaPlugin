package ua.co.tensa.modules.discord;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

final class DiscordSlashInteractionHandler implements AutoCloseable {
    private final String commandName;
    private final String guildId;
    private final DiscordGateway.SlashLinkHandler linkHandler;
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    DiscordSlashInteractionHandler(
            String commandName,
            String guildId,
            DiscordGateway.SlashLinkHandler linkHandler
    ) {
        this.commandName = Objects.requireNonNull(commandName, "commandName");
        this.guildId = Objects.requireNonNull(guildId, "guildId");
        this.linkHandler = Objects.requireNonNull(linkHandler, "linkHandler");
    }

    CompletableFuture<Boolean> handle(Interaction interaction) {
        Objects.requireNonNull(interaction, "interaction");
        if (!accepting.get() || !commandName.equals(interaction.commandName())) {
            return CompletableFuture.completedFuture(false);
        }

        // Discord invalidates an unacknowledged interaction after three seconds.
        // Deferring is deliberately the first asynchronous operation for every
        // matching command, before validation, storage, or role requests.
        return interaction.deferEphemeral().thenCompose(reply -> {
            CompletableFuture<DiscordEmbedMessage> response;
            if (!guildId.equals(interaction.guildId())) {
                response = CompletableFuture.completedFuture(DiscordEmbedMessage.linkError(
                        "Ця команда доступна лише на налаштованому Discord-сервері."
                ));
            } else {
                try {
                    response = linkHandler.link(
                            interaction.code(),
                            interaction.userId(),
                            interaction.userName()
                    ).exceptionally(ignored -> DiscordEmbedMessage.linkError(
                            "Не вдалося завершити прив'язку. Спробуйте ще раз пізніше."
                    ));
                } catch (RuntimeException error) {
                    response = CompletableFuture.completedFuture(DiscordEmbedMessage.linkError(
                            "Не вдалося завершити прив'язку. Спробуйте ще раз пізніше."
                    ));
                }
            }
            return response.thenCompose(reply::edit).thenApply(ignored -> true);
        });
    }

    @Override
    public void close() {
        accepting.set(false);
    }

    interface Interaction {
        String commandName();

        String guildId();

        String code();

        String userId();

        String userName();

        CompletableFuture<DeferredReply> deferEphemeral();
    }

    @FunctionalInterface
    interface DeferredReply {
        CompletableFuture<Void> edit(DiscordEmbedMessage message);
    }
}
