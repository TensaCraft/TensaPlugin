package ua.co.tensa.modules.meta;

import org.simpleyaml.configuration.file.YamlFile;
import ua.co.tensa.Tensa;
import ua.co.tensa.config.Database;
import ua.co.tensa.modules.meta.data.UserMetaConfig;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class UserMetaStore {
    public enum StorageType { DATABASE, FILE, MEMORY }

    private final StorageType storageType;
    private final Database db;
    private final YamlFile file;
    private final boolean defaultPersist;

    // session-only meta
    private final Map<UUID, Map<String, String>> sessionCache = new ConcurrentHashMap<>();
    // persistent cache (memory copy to speed up lookups)
    private final Map<UUID, Map<String, String>> cache = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Map<String, String>>> pendingLoads = new ConcurrentHashMap<>();
    private final ExecutorService fileExecutor;

    public UserMetaStore(Database db) {
        String type = UserMetaConfig.get().storageType;
        this.storageType = switch (type.toLowerCase()) {
            case "file" -> StorageType.FILE;
            case "memory" -> StorageType.MEMORY;
            default -> StorageType.DATABASE;
        };
        this.defaultPersist = UserMetaConfig.get().defaultPersist;
        this.db = db;
        String filePath;
        if (this.storageType == StorageType.FILE) {
            String path = UserMetaConfig.get().storageFile;
            filePath = Tensa.pluginPath.resolve(path).toString();
            this.file = new YamlFile(filePath);
            try {
                java.nio.file.Path parent = java.nio.file.Paths.get(filePath).getParent();
                if (parent != null) java.nio.file.Files.createDirectories(parent);
                if (!file.exists()) file.createNewFile(true);
                file.load();
                file.save();
            } catch (Exception e) {
            ua.co.tensa.Message.error("UserMeta file storage error: " + e.getMessage());
            }
        } else {
            this.file = null;
            filePath = null;
        }
        this.fileExecutor = this.storageType == StorageType.FILE
                ? Executors.newSingleThreadExecutor(runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("tensa-user-meta-file");
                    thread.setDaemon(true);
                    return thread;
                })
                : null;
    }

    public void ensureTable() {
        if (storageType == StorageType.DATABASE) {
            db.createTable("user_meta",
                    "id INT PRIMARY KEY AUTO_INCREMENT, " +
                            "uuid VARCHAR(36), " +
                            "meta_key VARCHAR(128), " +
                            "meta_value TEXT, " +
                            "UNIQUE KEY uniq_uuid_key (uuid, meta_key)"
            );
        }
    }

    public boolean getDefaultPersist() { return defaultPersist; }

    public Map<String, String> getAll(UUID uuid) {
        Map<String, String> persistent = cache.computeIfAbsent(uuid, this::loadAll);
        Map<String, String> session = sessionCache.computeIfAbsent(uuid, u -> new ConcurrentHashMap<>());
        Map<String, String> merged = new ConcurrentHashMap<>(persistent);
        merged.putAll(session);
        return merged;
    }

    public Map<String, String> getCached(UUID uuid) {
        Map<String, String> persistent = cache.getOrDefault(uuid, Map.of());
        Map<String, String> session = sessionCache.getOrDefault(uuid, Map.of());
        Map<String, String> merged = new ConcurrentHashMap<>(persistent);
        merged.putAll(session);
        return merged;
    }

    private Map<String, String> loadAll(UUID uuid) {
        Map<String, String> map = new ConcurrentHashMap<>();
        switch (storageType) {
            case DATABASE -> db.select("user_meta", "meta_key, meta_value", "uuid = ?",
                    rs -> {
                        while (rs.next()) {
                            map.put(rs.getString(1), rs.getString(2));
                        }
                        return null;
                    }, uuid.toString());
            case FILE -> {
                if (file != null) {
                    try { file.load(); } catch (Exception ignored) {}
                }
                if (file != null && file.contains(uuid.toString())) {
                    var section = file.getConfigurationSection(uuid.toString());
                    if (section != null) {
                        for (String key : section.getKeys(false)) {
                            map.put(key, section.getString(key, ""));
                        }
                    }
                }
            }
            case MEMORY -> {
                // nothing to load; memory-only
            }
        }
        return map;
    }

    private CompletableFuture<Map<String, String>> loadAllAsync(UUID uuid) {
        return switch (storageType) {
            case DATABASE -> db.selectAsync("user_meta", "meta_key, meta_value", "uuid = ?",
                    rs -> {
                        Map<String, String> map = new ConcurrentHashMap<>();
                        while (rs.next()) {
                            map.put(rs.getString(1), rs.getString(2));
                        }
                        return map;
                    }, uuid.toString());
            case FILE -> CompletableFuture.supplyAsync(() -> loadAll(uuid), fileExecutor);
            case MEMORY -> CompletableFuture.completedFuture(new ConcurrentHashMap<>());
        };
    }

    private CompletableFuture<Map<String, String>> ensurePersistentLoadedAsync(UUID uuid) {
        Map<String, String> existing = cache.get(uuid);
        if (existing != null) {
            return CompletableFuture.completedFuture(existing);
        }

        return pendingLoads.computeIfAbsent(uuid, id ->
                loadAllAsync(id).thenApply(loaded -> {
                    Map<String, String> persistent = new ConcurrentHashMap<>(loaded);
                    cache.put(id, persistent);
                    sessionCache.computeIfAbsent(id, ignored -> new ConcurrentHashMap<>());
                    return persistent;
                }).whenComplete((ignored, throwable) -> pendingLoads.remove(id))
        );
    }

    public String get(UUID uuid, String key) {
        Map<String, String> merged = getAll(uuid);
        return merged.getOrDefault(key, "");
    }

    public CompletableFuture<String> getAsync(UUID uuid, String key) {
        return getAllAsync(uuid).thenApply(map -> map.getOrDefault(key, ""));
    }

    public CompletableFuture<Map<String, String>> getAllAsync(UUID uuid) {
        return ensurePersistentLoadedAsync(uuid).thenApply(ignored -> getCached(uuid));
    }

    public void set(UUID uuid, String key, String value, boolean sessionOnly) {
        if (sessionOnly || storageType == StorageType.MEMORY) {
            sessionCache.computeIfAbsent(uuid, u -> new ConcurrentHashMap<>()).put(key, value);
            return;
        }
        cache.computeIfAbsent(uuid, u -> new ConcurrentHashMap<>()).put(key, value);
        switch (storageType) {
            case DATABASE -> {
                db.selectAsync("user_meta", "1", "uuid = ? AND meta_key = ?",
                        rs -> rs.next(),
                        uuid.toString(), key)
                        .thenAccept(exists -> {
                            if (Boolean.TRUE.equals(exists)) {
                                db.updateAsync("user_meta", "meta_value = ?", "uuid = ? AND meta_key = ?",
                                        value, uuid.toString(), key);
                            } else {
                                db.insertAsync("user_meta", "uuid, meta_key, meta_value",
                                        uuid.toString(), key, value);
                            }
                        })
                        .exceptionally(ex -> {
                            ua.co.tensa.Message.error("UserMeta save failed: " + ex.getMessage());
                            return null;
                        });
            }
            case FILE -> {
                if (file != null) {
                    CompletableFuture.runAsync(() -> {
                        try { file.load(); } catch (Exception ignored) {}
                        file.set(uuid.toString() + "." + key, value);
                        try { file.save(); } catch (Exception e) { ua.co.tensa.Message.error(e.getMessage()); }
                    }, fileExecutor);
                }
            }
        }
    }

    public void delete(UUID uuid, String key, boolean sessionOnly) {
        if (sessionOnly) {
            var s = sessionCache.get(uuid);
            if (s != null) s.remove(key);
            return;
        }
        switch (storageType) {
            case DATABASE -> db.deleteAsync("user_meta", "uuid = ? AND meta_key = ?", uuid.toString(), key)
                    .exceptionally(ex -> {
                        ua.co.tensa.Message.error("UserMeta delete failed: " + ex.getMessage());
                        return null;
                    });
            case FILE -> {
                if (file != null) {
                    CompletableFuture.runAsync(() -> {
                        try { file.load(); } catch (Exception ignored) {}
                        file.set(uuid.toString() + "." + key, null);
                        try { file.save(); } catch (Exception e) { ua.co.tensa.Message.error(e.getMessage()); }
                    }, fileExecutor);
                }
            }
            case MEMORY -> {}
        }
        var persistent = cache.get(uuid);
        if (persistent != null) persistent.remove(key);
        var session = sessionCache.get(uuid);
        if (session != null) session.remove(key);
    }

    public void preload(UUID uuid) {
        cache.put(uuid, loadAll(uuid));
        sessionCache.computeIfAbsent(uuid, u -> new ConcurrentHashMap<>());
    }

    public CompletableFuture<Void> preloadAsync(UUID uuid) {
        return ensurePersistentLoadedAsync(uuid).thenAccept(ignored -> sessionCache.computeIfAbsent(uuid, u -> new ConcurrentHashMap<>()));
    }

    public void close() {
        if (fileExecutor == null) {
            return;
        }
        fileExecutor.shutdown();
        try {
            if (!fileExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                fileExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            fileExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
