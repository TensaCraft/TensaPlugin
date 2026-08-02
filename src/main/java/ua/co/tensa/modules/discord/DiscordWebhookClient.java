package ua.co.tensa.modules.discord;

import com.google.gson.Gson;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

final class DiscordWebhookClient {
    private final DiscordSettings settings;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();

    DiscordWebhookClient(DiscordSettings settings) {
        this.settings = settings;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(settings.deliveryTimeout())
                .build();
    }

    boolean configured(DiscordRoute route) {
        return webhookUri(route).isPresent();
    }

    CompletableFuture<Void> send(DiscordRoute route, String content, String username, String avatarUrl) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", content);
        if (username != null && !username.isBlank()) {
            payload.put("username", username);
        }
        if (avatarUrl != null && !avatarUrl.isBlank()) {
            payload.put("avatar_url", avatarUrl);
        }
        return sendPayload(route, payload);
    }

    CompletableFuture<Void> sendEmbed(DiscordRoute route, DiscordEmbedMessage embed) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", embed.title());
        body.put("description", embed.description());
        body.put("color", embed.color());
        body.put("timestamp", embed.timestamp().toString());
        return sendPayload(route, new LinkedHashMap<>(Map.of("embeds", List.of(body))));
    }

    private CompletableFuture<Void> sendPayload(DiscordRoute route, Map<String, Object> payload) {
        return webhookUri(route)
                .map(uri -> {
                    payload.put("allowed_mentions", Map.of("parse", List.of()));
                    HttpRequest request = HttpRequest.newBuilder(uri)
                            .timeout(settings.deliveryTimeout())
                            .header("Content-Type", "application/json; charset=utf-8")
                            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload), StandardCharsets.UTF_8))
                            .build();
                    return httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                            .thenCompose(response -> response.statusCode() >= 200 && response.statusCode() < 300
                                    ? CompletableFuture.<Void>completedFuture(null)
                                    : CompletableFuture.<Void>failedFuture(
                                            new IllegalStateException("Discord webhook returned HTTP " + response.statusCode())
                                    ));
                })
                .orElseGet(() -> CompletableFuture.failedFuture(new IllegalStateException("Discord webhook is not configured")));
    }

    private Optional<URI> webhookUri(DiscordRoute route) {
        return route == DiscordRoute.EVENTS
                ? settings.credentials().eventsWebhookUri()
                : settings.credentials().webhookUri();
    }
}
