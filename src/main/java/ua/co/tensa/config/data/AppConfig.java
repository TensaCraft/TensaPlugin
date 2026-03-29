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
    @CfgKey(value = "language", comment = "Default language file under /langs")
    public String language = "en";

    @CfgKey(value = "use_uuid", comment = "Use UUID instead of name for player data")
    public boolean useUuid = false;

    // Modules section as deep map
    @CfgKey(value = "modules", comment = "Enable or disable individual Tensa modules by id")
    public Map<String, Object> modules = new LinkedHashMap<>();

    // Database
    @CfgKey(value = "database.enable", comment = "Enable the shared database connection for modules that support it")
    public boolean databaseEnable = false;

    @CfgKey(value = "database.type", comment = "Database driver: mysql, mariadb or h2")
    public String databaseType = "mysql";

    @CfgKey(value = "database.name", comment = "Database name or H2 file/database id")
    public String databaseName = "server";

    @CfgKey(value = "database.user", comment = "Database username")
    public String databaseUser = "root";

    @CfgKey(value = "database.password", comment = "Database password")
    public String databasePassword = "password";

    @CfgKey(value = "database.host", comment = "Database host")
    public String databaseHost = "localhost";

    @CfgKey(value = "database.port", comment = "Database port")
    public int databasePort = 3306;

    @CfgKey(value = "database.use_ssl", comment = "Enable SSL for the database connection when supported")
    public boolean useSsl = false;

    @CfgKey(value = "database.table_prefix", comment = "Prefix added to plugin-managed database tables")
    public String tablePrefix = "tensa_";

    @CfgKey(value = "velocity.log_cleanup.enable", comment = "Clean Velocity log files when the plugin starts")
    public boolean velocityLogCleanupEnable = false;

    @CfgKey(value = "velocity.log_cleanup.latest_log", comment = "Try to clear logs/latest.log on startup. This can fail on Windows if Velocity still holds the file handle")
    public boolean velocityLogCleanupLatestLog = false;

    @CfgKey(value = "velocity.log_cleanup.rotated_logs", comment = "Delete old uncompressed *.log files in the Velocity logs directory")
    public boolean velocityLogCleanupRotatedLogs = true;

    @CfgKey(value = "velocity.log_cleanup.compressed_logs", comment = "Delete compressed *.log.gz archives in the Velocity logs directory")
    public boolean velocityLogCleanupCompressedLogs = true;

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
