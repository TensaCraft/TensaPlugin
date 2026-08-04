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
                        () -> gateway.sendBotMessage(message.route(), message.botContent())
                );
            }
            return fallbackOnRejectedResponse(
                    gateway.sendBotEmbed(message.route(), message.embed()),
                    () -> gateway.sendBotMessage(message.route(), message.botContent())
            );
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
            if (invalidForm(cause)) {
                return fallback.get();
            }
            // Network failures and timeouts are ambiguous: the webhook may
            // already have accepted the request, so a fallback can duplicate it.
            return CompletableFuture.<Void>failedFuture(cause);
        }).thenCompose(java.util.function.Function.identity());
    }

    static boolean retryable(Throwable error) {
        Throwable cause = DiscordDiagnostics.unwrap(error);
        if (cause instanceof DiscordWebhookClient.RejectedResponseException rejected) {
            return rejected.retryable();
        }
        if (cause instanceof net.dv8tion.jda.api.exceptions.RateLimitedException) {
            return true;
        }
        if (cause instanceof net.dv8tion.jda.api.exceptions.ErrorResponseException response) {
            return response.isServerError();
        }
        return cause instanceof IllegalStateException
                && cause.getMessage() != null
                && cause.getMessage().startsWith("Discord gateway");
    }

    private static boolean invalidForm(Throwable cause) {
        return cause instanceof DiscordWebhookClient.RejectedResponseException rejected
                && rejected.invalidForm()
                || cause instanceof net.dv8tion.jda.api.exceptions.ErrorResponseException response
                && response.getErrorCode() == 50_035;
    }
}
