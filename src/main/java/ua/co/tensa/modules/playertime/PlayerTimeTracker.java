package ua.co.tensa.modules.playertime;

import ua.co.tensa.config.Database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerTimeTracker {
    private final Map<UUID, Long> playerOnlineTime;
    private final Database database;

    public PlayerTimeTracker(Database database) {
        this.playerOnlineTime = new ConcurrentHashMap<>();
        this.database = database;
    }

    public void playerJoined(UUID playerId, String playerName) {
        playerOnlineTime.put(playerId, System.currentTimeMillis());
        updatePlayerNameInDatabase(playerId, playerName);
    }


    public void playerLeft(UUID playerId) {
        Long joinTime = playerOnlineTime.get(playerId);
        if (joinTime != null) {
            long totalTimeOnline = System.currentTimeMillis() - joinTime;
            updatePlayerTimeInDatabase(playerId, totalTimeOnline);
            playerOnlineTime.remove(playerId);
        }
    }

    private void updatePlayerNameInDatabase(UUID playerId, String playerName) {
        if (database.exists("player_times", "uuid = ?", playerId.toString())) {
            database.updateAsync("player_times", "name = ?", "uuid = ?", playerName, playerId.toString());
        } else {
            database.insertAsync("player_times", "uuid, name, play_time", playerId.toString(), playerName, 0);
        }
    }

    private void updatePlayerTimeInDatabase(UUID playerId, long timeOnline) {
        database.updateAsync("player_times", "play_time = play_time + ?", "uuid = ?", timeOnline, playerId.toString());
    }

    public CompletableFuture<ResultSet> getPlayerTimeByName(String playerName) {
        return database.selectAsync("player_times", "play_time", "name = ?", playerName);
    }

    public CompletableFuture<ResultSet> getCurrentPlayerTime(UUID playerId) throws SQLException {
        return database.selectAsync("player_times", "play_time", "uuid = ?", playerId.toString());
    }

    public CompletableFuture<ResultSet> getTopPlayers(int limit) {
        String query = String.format("play_time > 0 ORDER BY play_time DESC LIMIT %d", limit);
        return database.selectAsync("player_times", "name, play_time", query);
    }

    public void updateAllOnlineTimes() {
        long currentTime = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> entry : playerOnlineTime.entrySet()) {
            Long joinTime = entry.getValue();
            if (joinTime != null) {
                long totalTimeOnline = currentTime - joinTime;
                updatePlayerTimeInDatabase(entry.getKey(), totalTimeOnline);
                playerOnlineTime.put(entry.getKey(), currentTime);
            }
        }
    }

}
