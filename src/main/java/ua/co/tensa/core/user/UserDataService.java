package ua.co.tensa.core.user;

import com.velocitypowered.api.proxy.Player;
import ua.co.tensa.core.storage.CoreStorageService;

import java.nio.file.Path;
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
    private final CoreStorageService ownedStorage;
    private final ExecutorService executor;

    private UserDataService(UserDataStore store, CoreStorageService ownedStorage) {
        this.store = store;
        this.ownedStorage = ownedStorage;
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
        CoreStorageService storage = CoreStorageService.local(databaseFile, tablePrefix);
        return new UserDataService(new JdbcUserDataStore(storage.dataSource(), storage.tablePrefix(), null), storage);
    }

    public static UserDataService createFromStorage(CoreStorageService storage) {
        return new UserDataService(new JdbcUserDataStore(storage.dataSource(), storage.tablePrefix(), null), null);
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
        if (ownedStorage != null) {
            ownedStorage.close();
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
