package ua.co.tensa.modules.playertime;

import ua.co.tensa.core.user.UserDataService;
import ua.co.tensa.core.user.UserProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PlayerTimeTracker {
    private final UserDataService userData;

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
        return CompletableFuture.supplyAsync(() -> userData.findUser(playerName)
                .map(UserProfile::totalPlayTimeSeconds)
                .map(seconds -> seconds * 1000L)
                .orElse(0L));
    }

    public CompletableFuture<Long> getCurrentPlayerTime(UUID playerId) {
        return userData.getPlayTimeAsync(playerId).thenApply(seconds -> seconds * 1000L);
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
}
