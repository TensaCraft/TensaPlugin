package ua.co.tensa.config;

import ua.co.tensa.config.data.AppConfig;
import ua.co.tensa.config.model.YamlAdapter;
import ua.co.tensa.config.model.YamlConfigPreflight;
import ua.co.tensa.Tensa;

import java.util.List;

/**
 * Instance-based config manager backed by a typed model (AppConfig).
 * YAML structure and keys remain unchanged.
 */
public class Config {
    private volatile AppConfig app;

    public Config() {
        this.app = loadCandidate();
    }

    public synchronized void reload() { app = loadCandidate(); }

    private static AppConfig loadCandidate() {
        // Reject broken input before the legacy YAML recovery code can replace it.
        YamlConfigPreflight.validate(Tensa.pluginPath.resolve("config.yml"));
        AppConfig candidate = new AppConfig();
        candidate.reloadCfg();
        return candidate;
    }

    public YamlAdapter adapter() { return app.adapter(); }

    // Convenience accessors mirroring previous API but using typed fields
    public List<String> getModules() { return app.moduleKeys(); }
    public boolean isModuleEnabled(String id) { return app.isModuleEnabled(id); }
    public String getLang() { return app.language; }

    public boolean databaseEnable() { return app.databaseEnable; }
    public String getDatabaseType() { return app.databaseType; }
    public String getDatabaseName() { return app.databaseName; }
    public String getDatabaseUser() { return app.databaseUser; }
    public String getDatabasePassword() { return app.databasePassword; }
    public String getDatabaseHost() { return app.databaseHost; }
    public int getDatabasePort() { return app.databasePort; }
    public boolean getSsl() { return app.useSsl; }
    public String getDatabaseTablePrefix() {
        String prefix = app.tablePrefix == null ? "" : app.tablePrefix.trim();
        if (prefix.isBlank()) {
            return "tpl_";
        }
        return prefix.endsWith("_") ? prefix : prefix + "_";
    }
    public String getStorageType() { return app.storageType; }
    public String getStorageLocalFile() { return app.storageLocalFile; }
    public boolean userMetaDefaultPersist() { return app.userMetaDefaultPersist; }
    public boolean useUUID() { return app.useUuid; }
    public boolean velocityLogCleanupEnable() { return app.velocityLogCleanupEnable; }
    public boolean velocityLogCleanupLatestLog() { return app.velocityLogCleanupLatestLog; }
    public boolean velocityLogCleanupRotatedLogs() { return app.velocityLogCleanupRotatedLogs; }
    public boolean velocityLogCleanupCompressedLogs() { return app.velocityLogCleanupCompressedLogs; }
}
