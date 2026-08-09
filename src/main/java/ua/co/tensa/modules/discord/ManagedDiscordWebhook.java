package ua.co.tensa.modules.discord;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** A runtime webhook credential whose string representation cannot reveal its token. */
final class ManagedDiscordWebhook {
    private final DiscordRoute route;
    private final String webhookId;
    private final URI uri;
    private final String tokenFingerprint;

    private ManagedDiscordWebhook(DiscordRoute route, String webhookId, URI uri, String tokenFingerprint) {
        this.route = Objects.requireNonNull(route, "route");
        this.webhookId = Objects.requireNonNull(webhookId, "webhookId");
        this.uri = Objects.requireNonNull(uri, "uri");
        this.tokenFingerprint = Objects.requireNonNull(tokenFingerprint, "tokenFingerprint");
    }

    static ManagedDiscordWebhook from(DiscordRoute route, String webhookId, URI uri) {
        String path = uri.getPath() == null ? "" : uri.getPath();
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(java.util.Locale.ROOT);
        int slash = path.lastIndexOf('/');
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !java.util.Set.of("discord.com", "ptb.discord.com", "canary.discord.com", "discordapp.com").contains(host)
                || !path.startsWith("/api/webhooks/" + webhookId + "/")
                || slash < 0 || slash == path.length() - 1) {
            throw new IllegalArgumentException("Managed Discord webhook URI is invalid");
        }
        return new ManagedDiscordWebhook(route, webhookId, uri, fingerprint(path.substring(slash + 1)));
    }

    DiscordRoute route() { return route; }
    String webhookId() { return webhookId; }
    URI uri() { return uri; }
    String tokenFingerprint() { return tokenFingerprint; }

    @Override
    public String toString() {
        return "ManagedDiscordWebhook[route=" + route + ", webhookId=" + webhookId + "]";
    }

    private static String fingerprint(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
