package ua.co.tensa.modules.discord;

import java.net.URI;
import java.util.Optional;

public final class DiscordCredentials {
    private final String botToken;
    private final URI webhookUri;

    DiscordCredentials(String botToken, URI webhookUri) {
        this.botToken = botToken;
        this.webhookUri = webhookUri;
    }

    public String botToken() {
        return botToken;
    }

    public Optional<URI> webhookUri() {
        return Optional.ofNullable(webhookUri);
    }

    @Override
    public String toString() {
        return "DiscordCredentials[botToken=<redacted>, webhookUri=<redacted>]";
    }
}
