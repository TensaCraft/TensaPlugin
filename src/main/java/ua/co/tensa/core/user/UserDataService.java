package ua.co.tensa.core.user;

import com.velocitypowered.api.proxy.Player;
import ua.co.tensa.core.storage.CoreStorageService;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class UserDataService implements AutoCloseable {
    private static final int MAX_PENDING_OPERATIONS = 2_048;
    private final UserDataStore store;
    private final CoreStorageService ownedStorage;
    private final ExecutorService executor;
    private final Set<CompletableFuture<?>> pendingOperations = ConcurrentHashMap.newKeySet();

    private UserDataService(UserDataStore store, CoreStorageService ownedStorage) {
        this.store = store;
        this.ownedStorage = ownedStorage;
        this.store.initialize();
        // Login/disconnect and metadata mutations must reach storage in submission order.
        // A bounded FIFO worker also makes reads submitted after a write observe that write.
        this.executor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(MAX_PENDING_OPERATIONS),
                runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("tensa-user-data-" + thread.threadId());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
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
        return supplyAsync(() -> recordLogin(data));
    }

    public void recordDisconnect(UUID uuid, long timestamp, String server) {
        store.recordDisconnect(uuid, timestamp, server);
    }

    public CompletableFuture<Void> recordDisconnectAsync(UUID uuid, long timestamp, String server) {
        return runAsync(() -> recordDisconnect(uuid, timestamp, server));
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
        return supplyAsync(() -> getAllMeta(uuid));
    }

    public Optional<String> getMeta(UUID uuid, String key) {
        return store.getMeta(uuid, key);
    }

    public Optional<String> getMeta(UUID uuid, String namespace, String key) {
        return getMeta(uuid, scopedMetaKey(namespace, key));
    }

    public CompletableFuture<Optional<String>> getMetaAsync(UUID uuid, String key) {
        return supplyAsync(() -> getMeta(uuid, key));
    }

    public CompletableFuture<Optional<String>> getMetaAsync(UUID uuid, String namespace, String key) {
        return supplyAsync(() -> getMeta(uuid, namespace, key));
    }

    public void setMeta(UUID uuid, String key, String value) {
        setMeta(uuid, key, value, "string");
    }

    public void setMeta(UUID uuid, String key, String value, String valueType) {
        store.setMeta(uuid, key, value, normalizeValueType(valueType));
    }

    public void setMeta(UUID uuid, String namespace, String key, String value, String valueType) {
        setMeta(uuid, scopedMetaKey(namespace, key), value, valueType);
    }

    public CompletableFuture<Void> setMetaAsync(UUID uuid, String key, String value) {
        return runAsync(() -> setMeta(uuid, key, value));
    }

    public CompletableFuture<Void> setMetaAsync(UUID uuid, String key, String value, String valueType) {
        return runAsync(() -> setMeta(uuid, key, value, valueType));
    }

    public CompletableFuture<Void> setMetaAsync(UUID uuid, String namespace, String key, String value, String valueType) {
        return runAsync(() -> setMeta(uuid, namespace, key, value, valueType));
    }

    public void deleteMeta(UUID uuid, String key) {
        store.deleteMeta(uuid, key);
    }

    public void deleteMeta(UUID uuid, String namespace, String key) {
        deleteMeta(uuid, scopedMetaKey(namespace, key));
    }

    public CompletableFuture<Void> deleteMetaAsync(UUID uuid, String key) {
        return runAsync(() -> deleteMeta(uuid, key));
    }

    public CompletableFuture<Void> deleteMetaAsync(UUID uuid, String namespace, String key) {
        return runAsync(() -> deleteMeta(uuid, namespace, key));
    }

    public Map<String, String> getMetaNamespace(UUID uuid, String namespace) {
        String prefix = normalizeNamespace(namespace) + ".";
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : getAllMeta(uuid).entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                values.put(entry.getKey().substring(prefix.length()), entry.getValue());
            }
        }
        return values;
    }

    public CompletableFuture<Map<String, String>> getMetaNamespaceAsync(UUID uuid, String namespace) {
        return supplyAsync(() -> getMetaNamespace(uuid, namespace));
    }

    public long getPlayTime(UUID uuid) {
        return store.getPlayTime(uuid);
    }

    public CompletableFuture<Long> getPlayTimeAsync(UUID uuid) {
        return supplyAsync(() -> getPlayTime(uuid));
    }

    public long getLivePlayTime(UUID uuid) {
        return getLivePlayTime(uuid, System.currentTimeMillis());
    }

    public long getLivePlayTime(UUID uuid, long timestamp) {
        return findByUuid(uuid)
                .map(profile -> calculateLivePlayTime(profile, timestamp))
                .orElse(0L);
    }

    public CompletableFuture<Long> getLivePlayTimeAsync(UUID uuid) {
        return supplyAsync(() -> getLivePlayTime(uuid));
    }

    public CompletableFuture<Long> getLivePlayTimeByNameAsync(String usernameOrUuid) {
        return supplyAsync(() -> findUser(usernameOrUuid)
                .map(profile -> calculateLivePlayTime(profile, System.currentTimeMillis()))
                .orElse(0L));
    }

    public void addPlayTime(UUID uuid, long seconds) {
        store.addPlayTime(uuid, seconds);
    }

    public List<UserProfile> topByPlayTime(int limit) {
        return store.topByPlayTime(limit);
    }

    public CompletableFuture<List<UserProfile>> topByPlayTimeAsync(int limit) {
        return supplyAsync(() -> topByPlayTime(limit));
    }

    @Override
    public void close() {
        synchronized (executor) {
            executor.shutdown();
        }
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                stopPendingOperations();
            }
        } catch (InterruptedException e) {
            stopPendingOperations();
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

    private <T> CompletableFuture<T> supplyAsync(Supplier<T> operation) {
        try {
            synchronized (executor) {
                CompletableFuture<T> result = CompletableFuture.supplyAsync(operation, executor);
                pendingOperations.add(result);
                result.whenComplete((value, failure) -> pendingOperations.remove(result));
                return result;
            }
        } catch (RejectedExecutionException rejected) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("User data operation queue is full", rejected)
            );
        }
    }

    private CompletableFuture<Void> runAsync(Runnable operation) {
        return supplyAsync(() -> {
            operation.run();
            return null;
        });
    }

    private void stopPendingOperations() {
        executor.shutdownNow();
        for (CompletableFuture<?> pending : pendingOperations) {
            pending.completeExceptionally(new IllegalStateException("User data service stopped before the operation completed"));
        }
    }

    private String scopedMetaKey(String namespace, String key) {
        String cleanKey = normalizeMetaKey(key);
        return normalizeNamespace(namespace) + "." + cleanKey;
    }

    private String normalizeNamespace(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("Meta namespace must not be blank");
        }
        return normalizeMetaKey(namespace);
    }

    private String normalizeMetaKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Meta key must not be blank");
        }
        return key.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }

    private String normalizeValueType(String valueType) {
        return valueType == null || valueType.isBlank() ? "string" : valueType.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private long calculateLivePlayTime(UserProfile profile, long timestamp) {
        long persisted = Math.max(0L, profile.totalPlayTimeSeconds());
        long onlineSince = profile.onlineSince();
        if (onlineSince <= 0 || timestamp <= onlineSince) {
            return persisted;
        }
        return persisted + ((timestamp - onlineSince) / 1000L);
    }
}
