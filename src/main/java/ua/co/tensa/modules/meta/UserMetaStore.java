package ua.co.tensa.modules.meta;

import org.simpleyaml.configuration.file.YamlFile;
import ua.co.tensa.Tensa;
import ua.co.tensa.config.Database;
import ua.co.tensa.modules.meta.data.UserMetaConfig;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    public String get(UUID uuid, String key) {
        Map<String, String> merged = getAll(uuid);
        return merged.getOrDefault(key, "");
    }

    public void set(UUID uuid, String key, String value, boolean sessionOnly) {
        if (sessionOnly || storageType == StorageType.MEMORY) {
            sessionCache.computeIfAbsent(uuid, u -> new ConcurrentHashMap<>()).put(key, value);
            return;
        }
        switch (storageType) {
            case DATABASE -> {
                boolean exists = db.exists("user_meta", "uuid = ? AND meta_key = ?", uuid.toString(), key);
                if (exists) {
                    db.update("user_meta", "meta_value = ?", "uuid = ? AND meta_key = ?", value, uuid.toString(), key);
                } else {
                    db.insert("user_meta", "uuid, meta_key, meta_value", uuid.toString(), key, value);
                }
            }
            case FILE -> {
                if (file != null) {
                    try { file.load(); } catch (Exception ignored) {}
                    file.set(uuid.toString() + "." + key, value);
                    try { file.save(); } catch (Exception e) { ua.co.tensa.Message.error(e.getMessage()); }
                }
            }
        }
        cache.computeIfAbsent(uuid, u -> new ConcurrentHashMap<>()).put(key, value);
    }

    public void delete(UUID uuid, String key, boolean sessionOnly) {
        if (sessionOnly) {
            var s = sessionCache.get(uuid);
            if (s != null) s.remove(key);
            return;
        }
        switch (storageType) {
            case DATABASE -> db.delete("user_meta", "uuid = ? AND meta_key = ?", uuid.toString(), key);
            case FILE -> {
                if (file != null) {
                    try { file.load(); } catch (Exception ignored) {}
                    file.set(uuid.toString() + "." + key, null);
                    try { file.save(); } catch (Exception e) { ua.co.tensa.Message.error(e.getMessage()); }
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
}
