package ua.co.tensa.config;

import ua.co.tensa.config.data.AppConfig;
import ua.co.tensa.config.model.YamlAdapter;

import java.util.List;

/**
 * Instance-based config manager backed by a typed model (AppConfig).
 * YAML structure and keys remain unchanged.
 */
public class Config {
    private final AppConfig app;

    public Config() {
        this.app = new AppConfig();
        // Ensure defaults are written and fields loaded after construction
        this.app.reloadCfg();
    }

    public void reload() { app.reloadCfg(); }

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
    public String getDatabaseTablePrefix() { return app.tablePrefix; }
    public boolean useUUID() { return app.useUuid; }
}
