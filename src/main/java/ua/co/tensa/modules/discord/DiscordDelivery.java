package ua.co.tensa.modules.discord;

import java.util.concurrent.CompletableFuture;

final class DiscordDelivery {
    private final DiscordGateway gateway;
    private final DiscordWebhookClient webhook;

    DiscordDelivery(DiscordGateway gateway, DiscordWebhookClient webhook) {
        this.gateway = gateway;
        this.webhook = webhook;
    }

    CompletableFuture<Void> send(DiscordOutboundMessage message) {
        if (message.embed() != null) {
            if (message.preferWebhook() && webhook.configured(message.route())) {
                return webhook.sendEmbed(message.route(), message.embed())
                        .exceptionallyCompose(ignored -> gateway.sendBotEmbed(message.route(), message.embed()));
            }
            return gateway.sendBotEmbed(message.route(), message.embed());
        }
        if (message.preferWebhook() && webhook.configured(message.route())) {
            return webhook.send(message.route(), message.webhookContent(), message.webhookUsername(), message.avatarUrl())
                    .exceptionallyCompose(ignored -> gateway.sendBotMessage(message.route(), message.botContent()));
        }
        return gateway.sendBotMessage(message.route(), message.botContent());
    }

    boolean requiresReadyGateway(DiscordOutboundMessage message) {
        return !message.preferWebhook() || !webhook.configured(message.route());
    }
}
