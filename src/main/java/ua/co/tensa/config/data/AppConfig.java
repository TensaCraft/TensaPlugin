package ua.co.tensa.config.data;

import ua.co.tensa.config.model.ConfigBase;
import ua.co.tensa.config.model.ann.CfgKey;
import ua.co.tensa.modules.ModuleProvider;
import ua.co.tensa.modules.TensaModule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Typed model for config.yml.
 * YAML structure is unchanged; this model provides typed accessors.
 */
public class AppConfig extends ConfigBase {

    // General
    @CfgKey(value = "language", comment = "Default language file under /lang")
    public String language = "en";

    @CfgKey(value = "use_uuid", comment = "Use UUID instead of name for player data")
    public boolean useUuid = false;

    // Modules section as deep map
    @CfgKey("modules")
    public Map<String, Object> modules = new LinkedHashMap<>();

    // Database
    @CfgKey("database.enable")
    public boolean databaseEnable = false;

    @CfgKey("database.type")
    public String databaseType = "mysql";

    @CfgKey("database.name")
    public String databaseName = "server";

    @CfgKey("database.user")
    public String databaseUser = "root";

    @CfgKey("database.password")
    public String databasePassword = "password";

    @CfgKey("database.host")
    public String databaseHost = "localhost";

    @CfgKey("database.port")
    public int databasePort = 3306;

    @CfgKey("database.use_ssl")
    public boolean useSsl = false;

    @CfgKey("database.table_prefix")
    public String tablePrefix = "tensa_";

    public AppConfig() {
        super("config.yml");
        // After base reload, field initializers are applied; seed module defaults if missing
        if (this.modules == null || this.modules.isEmpty()) {
            try {
                java.util.ServiceLoader<ModuleProvider> loader = java.util.ServiceLoader.load(ModuleProvider.class, AppConfig.class.getClassLoader());
                for (ModuleProvider p : loader) {
                    String id = p.id();
                    boolean enabled = true;
                    TensaModule ann = p.getClass().getAnnotation(TensaModule.class);
                    if (ann != null) enabled = ann.defaultEnabled();
                    this.modules.put(id, enabled);
                }
            } catch (Throwable ignored) {}
        }
    }

    // Convenience API similar to previous ConfigManager
    public List<String> moduleKeys() {
        return new ArrayList<>(modules.keySet());
    }

    public boolean isModuleEnabled(String key) {
        Object v = modules.get(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s);
        return false;
    }
}
