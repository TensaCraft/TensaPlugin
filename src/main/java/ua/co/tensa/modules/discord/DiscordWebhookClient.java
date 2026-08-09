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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

final class DiscordWebhookClient implements DiscordWebhookDelivery {
    private final DiscordSettings settings;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();
    private final ConcurrentMap<DiscordRoute, ManagedDiscordWebhook> managed = new ConcurrentHashMap<>();
    private final java.util.Set<DiscordRoute> invalidExplicit = ConcurrentHashMap.newKeySet();
    private final CopyOnWriteArrayList<Consumer<DiscordRoute>> invalidationHandlers = new CopyOnWriteArrayList<>();

    DiscordWebhookClient(DiscordSettings settings) {
        this.settings = settings;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(settings.deliveryTimeout())
                .build();
    }

    @Override
    public boolean configured(DiscordRoute route) {
        return webhookUri(route).isPresent();
    }

    @Override
    public CompletableFuture<Void> send(DiscordRoute route, String content, String username, String avatarUrl) {
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

    @Override
    public CompletableFuture<Void> sendEmbed(DiscordRoute route, DiscordEmbedMessage embed) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", embed.title());
        body.put("description", embed.description());
        body.put("color", embed.color());
        body.put("timestamp", embed.timestamp().toString());
        if (!embed.thumbnailUrl().isBlank()) {
            body.put("thumbnail", Map.of("url", embed.thumbnailUrl()));
        }
        if (!embed.footer().isBlank()) {
            body.put("footer", Map.of("text", embed.footer()));
        }
        return sendPayload(route, new LinkedHashMap<>(Map.of("embeds", List.of(body))));
    }

    void bind(ManagedDiscordWebhook webhook) {
        managed.put(webhook.route(), webhook);
    }

    void onInvalidated(Consumer<DiscordRoute> handler) {
        invalidationHandlers.add(java.util.Objects.requireNonNull(handler, "handler"));
    }

    boolean requiresManaged(DiscordRoute route) {
        return explicitWebhookUri(route).isEmpty() || invalidExplicit.contains(route);
    }

    Optional<String> managedWebhookId(DiscordRoute route) {
        ManagedDiscordWebhook webhook = managed.get(route);
        return webhook == null ? Optional.empty() : Optional.of(webhook.webhookId());
    }

    String chatTransportState() {
        if (!invalidExplicit.contains(DiscordRoute.CHAT)
                && explicitWebhookUri(DiscordRoute.CHAT).isPresent()) {
            return "explicit";
        }
        if (managed.containsKey(DiscordRoute.CHAT)) {
            return "managed";
        }
        return invalidExplicit.contains(DiscordRoute.CHAT) ? "recovering" : "unavailable";
    }

    @Override
    public void invalidate(DiscordRoute route) {
        if (explicitWebhookUri(route).isPresent()) {
            invalidExplicit.add(route);
        }
        managed.remove(route);
        invalidationHandlers.forEach(handler -> handler.accept(route));
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
                                            new RejectedResponseException(response.statusCode())
                                    ));
                })
                .orElseGet(() -> CompletableFuture.failedFuture(new IllegalStateException("Discord webhook is not configured")));
    }

    private Optional<URI> webhookUri(DiscordRoute route) {
        if (!invalidExplicit.contains(route)) {
            Optional<URI> explicit = explicitWebhookUri(route);
            if (explicit.isPresent()) {
                return explicit;
            }
        }
        ManagedDiscordWebhook dynamic = managed.get(route);
        return dynamic == null ? Optional.empty() : Optional.of(dynamic.uri());
    }

    private Optional<URI> explicitWebhookUri(DiscordRoute route) {
        return route == DiscordRoute.CHAT
                ? settings.credentials().webhookUri()
                : Optional.empty();
    }

    static final class RejectedResponseException extends IllegalStateException {
        private final int statusCode;

        RejectedResponseException(int statusCode) {
            super("Discord webhook returned HTTP " + statusCode);
            this.statusCode = statusCode;
        }

        boolean invalidForm() {
            return statusCode == 400;
        }

        boolean retryable() {
            return statusCode == 429 || statusCode >= 500;
        }

        boolean revoked() {
            return statusCode == 401 || statusCode == 404;
        }
    }
}
