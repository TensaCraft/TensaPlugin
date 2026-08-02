package ua.co.tensa.modules.discord;

import java.util.concurrent.CompletableFuture;

final class DiscordDelivery {
    private final DiscordGateway gateway;
    private final DiscordWebhookDelivery webhook;

    DiscordDelivery(DiscordGateway gateway, DiscordWebhookDelivery webhook) {
        this.gateway = gateway;
        this.webhook = webhook;
    }

    CompletableFuture<Void> send(DiscordOutboundMessage message) {
        if (message.embed() != null) {
            if (message.preferWebhook() && webhook.configured(message.route())) {
                return fallbackOnRejectedResponse(
                        webhook.sendEmbed(message.route(), message.embed()),
                        () -> gateway.sendBotEmbed(message.route(), message.embed())
                );
            }
            return gateway.sendBotEmbed(message.route(), message.embed());
        }
        if (message.preferWebhook() && webhook.configured(message.route())) {
            return fallbackOnRejectedResponse(
                    webhook.send(message.route(), message.webhookContent(), message.webhookUsername(), message.avatarUrl()),
                    () -> gateway.sendBotMessage(message.route(), message.botContent())
            );
        }
        return gateway.sendBotMessage(message.route(), message.botContent());
    }

    boolean requiresReadyGateway(DiscordOutboundMessage message) {
        return !message.preferWebhook() || !webhook.configured(message.route());
    }

    private static CompletableFuture<Void> fallbackOnRejectedResponse(
            CompletableFuture<Void> webhookAttempt,
            java.util.function.Supplier<CompletableFuture<Void>> fallback
    ) {
        return webhookAttempt.handle((ignored, error) -> {
            if (error == null) {
                return CompletableFuture.<Void>completedFuture(null);
            }
            Throwable cause = DiscordDiagnostics.unwrap(error);
            if (cause instanceof DiscordWebhookClient.RejectedResponseException) {
                return fallback.get();
            }
            // Network failures and timeouts are ambiguous: the webhook may
            // already have accepted the request, so a fallback can duplicate it.
            return CompletableFuture.<Void>failedFuture(cause);
        }).thenCompose(java.util.function.Function.identity());
    }
}
