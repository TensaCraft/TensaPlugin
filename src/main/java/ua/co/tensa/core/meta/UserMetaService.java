package ua.co.tensa.core.meta;

import ua.co.tensa.core.user.UserDataService;

import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class UserMetaService implements AutoCloseable {
    private final UserDataService userData;
    private final boolean defaultPersist;
    private final Map<UUID, Map<String, String>> sessionCache = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, String>> persistentCache = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Map<String, String>>> pendingLoads = new ConcurrentHashMap<>();
    private final Set<UUID> loadedUsers = new HashSet<>();
    private final Map<UUID, Set<String>> pendingDeletes = new HashMap<>();
    private boolean closed;

    public UserMetaService(UserDataService userData, boolean defaultPersist) {
        if (userData == null) {
            throw new IllegalArgumentException("userData must not be null");
        }
        this.userData = userData;
        this.defaultPersist = defaultPersist;
    }

    public boolean defaultPersist() {
        return defaultPersist;
    }

    public Map<String, String> getAll(UUID uuid) {
        CompletableFuture<Map<String, String>> pending;
        synchronized (this) {
            if (closed) throw new IllegalStateException("User metadata service is closed");
            if (loadedUsers.contains(uuid)) return getCached(uuid);
            pending = pendingLoads.computeIfAbsent(uuid, ignored -> new CompletableFuture<>());
        }
        // Keep the synchronous API synchronous: its caller may itself be a data-worker
        // continuation, where joining a newly queued read would deadlock that worker.
        try {
            completePersistentLoad(uuid, pending, userData.getAllMeta(uuid), null);
        } catch (RuntimeException failure) {
            completePersistentLoad(uuid, pending, null, failure);
        }
        return pending.thenApply(ignored -> getCached(uuid)).join();
    }

    public synchronized Map<String, String> getCached(UUID uuid) {
        return merge(uuid, persistentCache.getOrDefault(uuid, Map.of()));
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

    public synchronized void set(UUID uuid, String key, String value, boolean sessionOnly) {
        if (closed) return;
        if (sessionOnly) {
            sessionCache.computeIfAbsent(uuid, ignored -> new ConcurrentHashMap<>()).put(key, value);
            return;
        }

        ensurePersistentLoadedAsync(uuid);
        persistentCache.computeIfAbsent(uuid, ignored -> new ConcurrentHashMap<>()).put(key, value);
        Set<String> deleted = pendingDeletes.get(uuid);
        if (deleted != null) deleted.remove(key);
        userData.setMetaAsync(uuid, key, value).exceptionally(ex -> {
            ua.co.tensa.Message.error("UserMeta save failed: " + ex.getMessage());
            return null;
        });
    }

    public synchronized void delete(UUID uuid, String key, boolean sessionOnly) {
        if (closed) return;
        if (sessionOnly) {
            Map<String, String> session = sessionCache.get(uuid);
            if (session != null) {
                session.remove(key);
            }
            return;
        }

        ensurePersistentLoadedAsync(uuid);
        if (!loadedUsers.contains(uuid)) {
            pendingDeletes.computeIfAbsent(uuid, ignored -> new HashSet<>()).add(key);
        }
        Map<String, String> persistent = persistentCache.get(uuid);
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
        getAll(uuid);
    }

    public CompletableFuture<Void> preloadAsync(UUID uuid) {
        return ensurePersistentLoadedAsync(uuid).thenAccept(ignored -> { });
    }

    public synchronized void forget(UUID uuid) {
        if (uuid == null) {
            return;
        }
        sessionCache.remove(uuid);
        persistentCache.remove(uuid);
        loadedUsers.remove(uuid);
        pendingDeletes.remove(uuid);
        CompletableFuture<Map<String, String>> pending = pendingLoads.remove(uuid);
        if (pending != null) {
            pending.cancel(false);
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        sessionCache.clear();
        persistentCache.clear();
        var pending = java.util.List.copyOf(pendingLoads.values());
        pendingLoads.clear();
        loadedUsers.clear();
        pendingDeletes.clear();
        pending.forEach(future -> future.cancel(false));
    }

    private synchronized CompletableFuture<Map<String, String>> ensurePersistentLoadedAsync(UUID uuid) {
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("User metadata service is closed"));
        }
        Map<String, String> existing = persistentCache.get(uuid);
        if (existing != null && loadedUsers.contains(uuid)) {
            return CompletableFuture.completedFuture(existing);
        }

        CompletableFuture<Map<String, String>> pending = pendingLoads.get(uuid);
        if (pending != null) return pending;

        CompletableFuture<Map<String, String>> result = new CompletableFuture<>();
        // Publish before attaching callbacks: an already completed future may run them inline.
        pendingLoads.put(uuid, result);
        userData.getAllMetaAsync(uuid).whenComplete((loaded, failure) ->
                completePersistentLoad(uuid, result, loaded, failure));
        return result;
    }

    private void completePersistentLoad(UUID uuid, CompletableFuture<Map<String, String>> result,
                                        Map<String, String> loaded, Throwable failure) {
        Map<String, String> persistent = null;
        Throwable completionFailure = failure;
        synchronized (this) {
            if (pendingLoads.get(uuid) != result) return;
            pendingLoads.remove(uuid);
            if (failure == null) {
                try {
                    persistent = new ConcurrentHashMap<>(loaded);
                    persistent.putAll(persistentCache.getOrDefault(uuid, Map.of()));
                    Set<String> deleted = pendingDeletes.remove(uuid);
                    if (deleted != null) deleted.forEach(persistent::remove);
                    persistentCache.put(uuid, persistent);
                    loadedUsers.add(uuid);
                } catch (RuntimeException invalidData) {
                    completionFailure = invalidData;
                }
            }
        }
        if (completionFailure != null) result.completeExceptionally(completionFailure);
        else result.complete(persistent);
    }

    private Map<String, String> merge(UUID uuid, Map<String, String> persistent) {
        Map<String, String> merged = new ConcurrentHashMap<>(persistent);
        merged.putAll(sessionCache.getOrDefault(uuid, Map.of()));
        return merged;
    }
}
