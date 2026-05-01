package ua.co.tensa.modules.playertime;

import ua.co.tensa.core.user.UserDataService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

public class PlayerTimeTracker {
    private final UserDataService userData;
    private final ConcurrentMap<UUID, CompletableFuture<Long>> currentRequests = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CompletableFuture<Long>> namedRequests = new ConcurrentHashMap<>();

    public record PlayerTimeEntry(String playerName, long playTime) {}

    public PlayerTimeTracker(UserDataService userData) {
        this.userData = userData;
    }

    public void playerJoined(UUID playerId, String playerName) {
        // Core events record login/session state.
    }

    public void playerLeft(UUID playerId) {
        // Core events record disconnect/session duration.
    }

    public CompletableFuture<Long> getPlayerTimeByName(String playerName) {
        String key = normalizeName(playerName);
        if (key.isBlank()) {
            return CompletableFuture.completedFuture(0L);
        }
        return singleFlight(namedRequests, key,
                () -> userData.getLivePlayTimeByNameAsync(playerName).thenApply(seconds -> seconds * 1000L));
    }

    public CompletableFuture<Long> getCurrentPlayerTime(UUID playerId) {
        if (playerId == null) {
            return CompletableFuture.completedFuture(0L);
        }
        return singleFlight(currentRequests, playerId,
                () -> userData.getLivePlayTimeAsync(playerId).thenApply(seconds -> seconds * 1000L));
    }

    public CompletableFuture<List<PlayerTimeEntry>> getTopPlayers(int limit) {
        if (limit < 1 || limit > 1000) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }

        return userData.topByPlayTimeAsync(limit)
                .thenApply(profiles -> profiles.stream()
                        .map(profile -> new PlayerTimeEntry(profile.username(), profile.totalPlayTimeSeconds() * 1000L))
                        .toList())
                .exceptionally(ex -> {
                    ua.co.tensa.Message.error("Failed to get top players: " + ex.getMessage());
                    return new ArrayList<>();
                });
    }

    public void updateAllOnlineTimes() {
        // Playtime is committed by the core disconnect event.
    }

    private <T> CompletableFuture<Long> singleFlight(
            ConcurrentMap<T, CompletableFuture<Long>> requests,
            T key,
            Supplier<CompletableFuture<Long>> loader
    ) {
        return requests.computeIfAbsent(key, ignored -> {
            CompletableFuture<Long> tracked = new CompletableFuture<>();
            loader.get().whenComplete((value, throwable) -> {
                requests.remove(key, tracked);
                if (throwable != null) {
                    tracked.completeExceptionally(throwable);
                } else {
                    tracked.complete(value);
                }
            });
            return tracked;
        });
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
