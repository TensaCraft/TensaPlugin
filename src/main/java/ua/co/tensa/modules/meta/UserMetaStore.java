package ua.co.tensa.modules.meta;

import ua.co.tensa.Tensa;
import ua.co.tensa.core.user.UserDataService;
import ua.co.tensa.modules.meta.data.UserMetaConfig;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class UserMetaStore {
    private final UserDataService userData;
    private final boolean defaultPersist;
    private final Map<UUID, Map<String, String>> sessionCache = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, String>> cache = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Map<String, String>>> pendingLoads = new ConcurrentHashMap<>();

    public UserMetaStore() {
        if (Tensa.userData == null) {
            throw new IllegalStateException("UserMeta requires core user data service");
        }
        this.userData = Tensa.userData;
        UserMetaConfig config = UserMetaConfig.get();
        config.reloadCfg();
        this.defaultPersist = config.defaultPersist;
    }

    public void ensureTable() {
        // Tables are initialized by the core user data service.
    }

    public boolean getDefaultPersist() {
        return defaultPersist;
    }

    public Map<String, String> getAll(UUID uuid) {
        Map<String, String> persistent = cache.computeIfAbsent(uuid, userData::getAllMeta);
        return merge(uuid, persistent);
    }

    public Map<String, String> getCached(UUID uuid) {
        return merge(uuid, cache.getOrDefault(uuid, Map.of()));
    }

    public String get(UUID uuid, String key) {
        return getAll(uuid).getOrDefault(key, "");
    }

    public CompletableFuture<String> getAsync(UUID uuid, String key) {
        return getAllAsync(uuid).thenApply(map -> map.getOrDefault(key, ""));
    }

    public CompletableFuture<Map<String, String>> getAllAsync(UUID uuid) {
        return ensurePersistentLoadedAsync(uuid).thenApply(ignored -> getCached(uuid));
    }

    public void set(UUID uuid, String key, String value, boolean sessionOnly) {
        if (sessionOnly) {
            sessionCache.computeIfAbsent(uuid, ignored -> new ConcurrentHashMap<>()).put(key, value);
            return;
        }

        cache.computeIfAbsent(uuid, ignored -> new ConcurrentHashMap<>()).put(key, value);
        userData.setMetaAsync(uuid, key, value).exceptionally(ex -> {
            ua.co.tensa.Message.error("UserMeta save failed: " + ex.getMessage());
            return null;
        });
    }

    public void delete(UUID uuid, String key, boolean sessionOnly) {
        if (sessionOnly) {
            Map<String, String> session = sessionCache.get(uuid);
            if (session != null) {
                session.remove(key);
            }
            return;
        }

        Map<String, String> persistent = cache.get(uuid);
        if (persistent != null) {
            persistent.remove(key);
        }
        Map<String, String> session = sessionCache.get(uuid);
        if (session != null) {
            session.remove(key);
        }
        userData.deleteMetaAsync(uuid, key).exceptionally(ex -> {
            ua.co.tensa.Message.error("UserMeta delete failed: " + ex.getMessage());
            return null;
        });
    }

    public void preload(UUID uuid) {
        cache.put(uuid, userData.getAllMeta(uuid));
        sessionCache.computeIfAbsent(uuid, ignored -> new ConcurrentHashMap<>());
    }

    public CompletableFuture<Void> preloadAsync(UUID uuid) {
        return ensurePersistentLoadedAsync(uuid)
                .thenAccept(ignored -> sessionCache.computeIfAbsent(uuid, u -> new ConcurrentHashMap<>()));
    }

    public void close() {
        sessionCache.clear();
        cache.clear();
        pendingLoads.clear();
    }

    private CompletableFuture<Map<String, String>> ensurePersistentLoadedAsync(UUID uuid) {
        Map<String, String> existing = cache.get(uuid);
        if (existing != null) {
            return CompletableFuture.completedFuture(existing);
        }

        return pendingLoads.computeIfAbsent(uuid, id ->
                userData.getAllMetaAsync(id).thenApply(loaded -> {
                    Map<String, String> persistent = new ConcurrentHashMap<>(loaded);
                    cache.put(id, persistent);
                    return persistent;
                }).whenComplete((ignored, throwable) -> pendingLoads.remove(id))
        );
    }

    private Map<String, String> merge(UUID uuid, Map<String, String> persistent) {
        Map<String, String> merged = new ConcurrentHashMap<>(persistent);
        merged.putAll(sessionCache.getOrDefault(uuid, Map.of()));
        return merged;
    }
}
