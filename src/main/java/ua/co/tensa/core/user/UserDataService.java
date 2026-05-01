package ua.co.tensa.core.user;

import com.velocitypowered.api.proxy.Player;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;
import ua.co.tensa.Message;
import ua.co.tensa.Tensa;
import ua.co.tensa.config.Database;
import ua.co.tensa.config.model.YamlFileIO;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class UserDataService implements AutoCloseable {
    private final UserDataStore store;
    private final ExecutorService executor;

    private UserDataService(UserDataStore store) {
        this.store = store;
        this.store.initialize();
        this.executor = Executors.newFixedThreadPool(
                Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors())),
                runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("tensa-user-data-" + thread.threadId());
                    thread.setDaemon(true);
                    return thread;
                });
    }

    public static UserDataService local(Path databaseFile, String tablePrefix) {
        HikariDataSource dataSource = localDataSource(databaseFile);
        return new UserDataService(new JdbcUserDataStore(dataSource, tablePrefix, dataSource));
    }

    public static UserDataService external(DataSource dataSource, String tablePrefix) {
        return new UserDataService(new JdbcUserDataStore(dataSource, tablePrefix, null));
    }

    public static UserDataService createFromConfig(Database database) {
        String mode = Tensa.config == null ? "auto" : Tensa.config.getStorageType();
        String prefix = Tensa.config == null ? "tensa_" : Tensa.config.getDatabaseTablePrefix();

        if ("database".equalsIgnoreCase(mode)) {
            if (database == null || !database.enabled || database.getDataSource() == null) {
                throw new IllegalStateException("storage.type=database requires an active configured database connection");
            }
            return external(database.getDataSource(), prefix);
        }

        if ("auto".equalsIgnoreCase(mode) && database != null && database.enabled && database.getDataSource() != null) {
            return external(database.getDataSource(), prefix);
        }

        Path localFile = Tensa.pluginPath.resolve(Tensa.config == null ? "storage/tensa-users" : Tensa.config.getStorageLocalFile());
        return local(localFile, prefix);
    }

    public UserRecordResult recordLogin(UserLoginData data) {
        return store.recordLogin(data);
    }

    public CompletableFuture<UserRecordResult> recordLoginAsync(UserLoginData data) {
        return CompletableFuture.supplyAsync(() -> recordLogin(data), executor);
    }

    public void recordDisconnect(UUID uuid, long timestamp, String server) {
        store.recordDisconnect(uuid, timestamp, server);
    }

    public CompletableFuture<Void> recordDisconnectAsync(UUID uuid, long timestamp, String server) {
        return CompletableFuture.runAsync(() -> recordDisconnect(uuid, timestamp, server), executor);
    }

    public Optional<UserProfile> findUser(String usernameOrUuid) {
        if (usernameOrUuid == null || usernameOrUuid.isBlank()) {
            return Optional.empty();
        }
        try {
            return store.findByUuid(UUID.fromString(usernameOrUuid.trim()));
        } catch (IllegalArgumentException ignored) {
            return store.findByName(usernameOrUuid.trim());
        }
    }

    public Optional<UserProfile> findByUuid(UUID uuid) {
        return store.findByUuid(uuid);
    }

    public Map<String, String> getAllMeta(UUID uuid) {
        return store.getAllMeta(uuid);
    }

    public CompletableFuture<Map<String, String>> getAllMetaAsync(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> getAllMeta(uuid), executor);
    }

    public Optional<String> getMeta(UUID uuid, String key) {
        return store.getMeta(uuid, key);
    }

    public CompletableFuture<Optional<String>> getMetaAsync(UUID uuid, String key) {
        return CompletableFuture.supplyAsync(() -> getMeta(uuid, key), executor);
    }

    public void setMeta(UUID uuid, String key, String value) {
        store.setMeta(uuid, key, value, "string");
    }

    public CompletableFuture<Void> setMetaAsync(UUID uuid, String key, String value) {
        return CompletableFuture.runAsync(() -> setMeta(uuid, key, value), executor);
    }

    public void deleteMeta(UUID uuid, String key) {
        store.deleteMeta(uuid, key);
    }

    public CompletableFuture<Void> deleteMetaAsync(UUID uuid, String key) {
        return CompletableFuture.runAsync(() -> deleteMeta(uuid, key), executor);
    }

    public long getPlayTime(UUID uuid) {
        return store.getPlayTime(uuid);
    }

    public CompletableFuture<Long> getPlayTimeAsync(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> getPlayTime(uuid), executor);
    }

    public void addPlayTime(UUID uuid, long seconds) {
        store.addPlayTime(uuid, seconds);
    }

    public List<UserProfile> topByPlayTime(int limit) {
        return store.topByPlayTime(limit);
    }

    public CompletableFuture<List<UserProfile>> topByPlayTimeAsync(int limit) {
        return CompletableFuture.supplyAsync(() -> topByPlayTime(limit), executor);
    }

    public void importLegacyData(Path pluginPath, Database database) {
        importLegacyFirstJoin(pluginPath.resolve("events").resolve("first-join.yml"));
        importLegacyUserMeta(pluginPath.resolve("user_meta").resolve("data.yml"));
        importLegacyPlayerTimes(database);
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        store.close();
    }

    private static HikariDataSource localDataSource(Path databaseFile) {
        try {
            Path parent = databaseFile.toAbsolutePath().normalize().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to prepare local user database directory: " + e.getMessage(), e);
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:file:" + databaseFile.toAbsolutePath().normalize() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setPoolName("TensaUserDataLocal");
        return new HikariDataSource(config);
    }

    private void importLegacyFirstJoin(Path file) {
        String migrationId = "legacy_first_join_yml_v1";
        if (store.migrationApplied(migrationId) || !Files.exists(file)) {
            return;
        }

        try {
            YamlConfigurationLoader loader = YamlFileIO.loader(file);
            CommentedConfigurationNode root = loader.load();
            CommentedConfigurationNode players = root.node("players");
            int imported = 0;
            for (Map.Entry<Object, ? extends CommentedConfigurationNode> entry : players.childrenMap().entrySet()) {
                try {
                    UUID uuid = UUID.fromString(String.valueOf(entry.getKey()));
                    CommentedConfigurationNode node = entry.getValue();
                    String username = node.node("name").getString("");
                    long firstSeenAt = parseTimestamp(node.node("first_seen_at").getString(""));
                    if (store.findByUuid(uuid).isEmpty()) {
                        store.recordLogin(UserLoginData.builder(uuid, username == null || username.isBlank() ? uuid.toString() : username)
                                .timestamp(firstSeenAt > 0 ? firstSeenAt : System.currentTimeMillis())
                                .build());
                        store.recordDisconnect(uuid, firstSeenAt > 0 ? firstSeenAt : System.currentTimeMillis(), "");
                    }
                    imported++;
                } catch (IllegalArgumentException ignored) {
                    Message.warn("User data migration: ignored invalid first-join UUID '" + entry.getKey() + "'");
                }
            }
            store.markMigrationApplied(migrationId);
            Message.info("User data migration: imported " + imported + " first-join entries");
        } catch (Exception e) {
            Message.warn("User data migration: failed to import first-join YAML: " + e.getMessage());
        }
    }

    private void importLegacyUserMeta(Path file) {
        String migrationId = "legacy_user_meta_yml_v1";
        if (store.migrationApplied(migrationId) || !Files.exists(file)) {
            return;
        }

        try {
            YamlConfigurationLoader loader = YamlFileIO.loader(file);
            CommentedConfigurationNode root = loader.load();
            int imported = 0;
            for (Map.Entry<Object, ? extends CommentedConfigurationNode> userEntry : root.childrenMap().entrySet()) {
                try {
                    UUID uuid = UUID.fromString(String.valueOf(userEntry.getKey()));
                    for (Map.Entry<Object, ? extends CommentedConfigurationNode> metaEntry : userEntry.getValue().childrenMap().entrySet()) {
                        store.setMeta(uuid, String.valueOf(metaEntry.getKey()), metaEntry.getValue().getString(""), "string");
                        imported++;
                    }
                } catch (IllegalArgumentException ignored) {
                    Message.warn("User data migration: ignored invalid user-meta UUID '" + userEntry.getKey() + "'");
                }
            }
            store.markMigrationApplied(migrationId);
            Message.info("User data migration: imported " + imported + " user-meta entries");
        } catch (Exception e) {
            Message.warn("User data migration: failed to import user_meta YAML: " + e.getMessage());
        }
    }

    private void importLegacyPlayerTimes(Database database) {
        String migrationId = "legacy_player_times_table_v1";
        if (store.migrationApplied(migrationId) || database == null || !database.enabled || !database.tableExists("player_times")) {
            return;
        }

        try {
            Integer imported = database.select("player_times", "uuid, name, play_time", "1 = 1",
                    (ResultSet rs) -> {
                        int count = 0;
                        while (rs.next()) {
                            UUID uuid = UUID.fromString(rs.getString("uuid"));
                            String name = rs.getString("name");
                            long playMillis = rs.getLong("play_time");
                            if (store.findByUuid(uuid).isEmpty()) {
                                store.recordLogin(UserLoginData.builder(uuid, name).build());
                                store.recordDisconnect(uuid, System.currentTimeMillis(), "");
                            }
                            store.addPlayTime(uuid, Math.max(0L, playMillis / 1000L));
                            count++;
                        }
                        return count;
                    });
            store.markMigrationApplied(migrationId);
            Message.info("User data migration: imported " + (imported == null ? 0 : imported) + " player-time rows");
        } catch (Exception e) {
            Message.warn("User data migration: failed to import player_times table: " + e.getMessage());
        }
    }

    private long parseTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Instant.parse(value).toEpochMilli();
        } catch (DateTimeParseException ignored) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ignoredAgain) {
                return 0L;
            }
        }
    }

    public static UserLoginData fromPlayer(Player player) {
        String server = player.getCurrentServer()
                .map(connection -> connection.getServerInfo().getName())
                .orElse("");
        String host = player.getVirtualHost()
                .map(address -> address.getHostString())
                .orElse("");
        return UserLoginData.builder(player.getUniqueId(), player.getUsername())
                .ip(player.getRemoteAddress() == null ? "" : player.getRemoteAddress().getHostString())
                .virtualHost(host)
                .protocolVersion(String.valueOf(player.getProtocolVersion()))
                .server(server)
                .build();
    }
}
