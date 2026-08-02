package ua.co.tensa.modules.discord;

import java.net.URI;
import java.util.Optional;

public final class DiscordCredentials {
    private final String botToken;
    private final URI webhookUri;
    private final URI eventsWebhookUri;

    DiscordCredentials(String botToken, URI webhookUri, URI eventsWebhookUri) {
        this.botToken = botToken;
        this.webhookUri = webhookUri;
        this.eventsWebhookUri = eventsWebhookUri;
    }

    public String botToken() {
        return botToken;
    }

    public Optional<URI> webhookUri() {
        return Optional.ofNullable(webhookUri);
    }

    public Optional<URI> eventsWebhookUri() {
        return Optional.ofNullable(eventsWebhookUri);
    }

    @Override
    public String toString() {
        return "DiscordCredentials[botToken=<redacted>, webhookUri=<redacted>, eventsWebhookUri=<redacted>]";
    }
}
