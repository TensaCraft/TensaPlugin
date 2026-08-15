package ua.co.tensa.config.data;

import ua.co.tensa.config.model.ConfigBase;
import ua.co.tensa.config.model.ann.CfgKey;
import ua.co.tensa.modules.ModuleProvider;
import ua.co.tensa.modules.TensaModule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Typed model for config.yml.
 * YAML structure is unchanged; this model provides typed accessors.
 */
public class AppConfig extends ConfigBase {
    private static final Set<String> LEGACY_COMMUNICATION_MODULES = Set.of("chat-manager", "chat", "discord");

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
    public String tablePrefix = "tpl_";

    @CfgKey(value = "storage.type", comment = "User data storage: auto uses configured database when available, otherwise local H2. Valid values: auto, database, local")
    public String storageType = "auto";

    @CfgKey(value = "storage.local_file", comment = "Local H2 file used for core user data when storage.type is local or auto fallback")
    public String storageLocalFile = "storage/tensa-users";

    @CfgKey(value = "user_meta.default_persist", comment = "Persist /tmeta values by default. Use --session for temporary values")
    public boolean userMetaDefaultPersist = true;

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
            this.modules.putAll(discoverModuleDefaults());
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

    @Override
    public synchronized void reloadCfg() {
        super.reloadCfg();
        Map<String, Object> supportedDefaults = discoverModuleDefaults();
        boolean migrated = migrateLegacyCommunicationsFlag();
        migrated |= migrateModuleId("request-module", "requests");
        boolean changed = migrated;
        changed |= addMissingSupportedModuleKeys(supportedDefaults);
        changed |= removeUnsupportedModuleKeys(supportedDefaults.keySet());
        if (changed) {
            save();
        }
    }

    private boolean migrateModuleId(String legacyId, String currentId) {
        if (modules == null || !modules.containsKey(legacyId)) {
            return false;
        }
        if (!modules.containsKey(currentId)) {
            Object value = modules.get(legacyId);
            modules.put(currentId, value);
            setNodeValue(node("modules." + currentId), value);
        }
        modules.remove(legacyId);
        setNodeValue(node("modules." + legacyId), null);
        ua.co.tensa.Message.info("Migrated module id " + legacyId + " to " + currentId);
        return true;
    }

    private boolean migrateLegacyCommunicationsFlag() {
        if (modules == null || modules.containsKey("communications")) {
            return false;
        }
        boolean hasLegacyFlag = LEGACY_COMMUNICATION_MODULES.stream().anyMatch(modules::containsKey);
        if (!hasLegacyFlag) {
            return false;
        }
        boolean enabled = LEGACY_COMMUNICATION_MODULES.stream()
                .filter(modules::containsKey)
                .map(modules::get)
                .anyMatch(AppConfig::booleanValue);
        modules.put("communications", enabled);
        setNodeValue(node("modules.communications"), enabled);
        ua.co.tensa.Message.info("Migrated legacy chat/Discord module flags to modules.communications");
        return true;
    }

    private boolean removeUnsupportedModuleKeys(Set<String> supported) {
        if (modules == null || modules.isEmpty() || supported.isEmpty()) {
            return false;
        }
        Set<String> removed = new LinkedHashSet<>();
        for (String key : new ArrayList<>(modules.keySet())) {
            if (supported.contains(key)) {
                continue;
            }
            modules.remove(key);
            setNodeValue(node("modules." + key), null);
            removed.add(key);
        }
        if (!removed.isEmpty()) {
            ua.co.tensa.Message.warn("Removed unsupported module config keys: " + String.join(", ", removed));
        }
        return !removed.isEmpty();
    }

    private boolean addMissingSupportedModuleKeys(Map<String, Object> supportedDefaults) {
        boolean changed = false;
        for (Map.Entry<String, Object> entry : supportedDefaults.entrySet()) {
            if (modules.containsKey(entry.getKey())) {
                continue;
            }
            modules.put(entry.getKey(), entry.getValue());
            setNodeValue(node("modules." + entry.getKey()), entry.getValue());
            changed = true;
        }
        return changed;
    }

    @Override
    protected boolean shouldWriteDefault(String basePath, Object defaultValue,
                                         org.spongepowered.configurate.CommentedConfigurationNode yaml) {
        if ("modules".equals(basePath)
                && defaultValue instanceof Map<?, ?> defaults
                && defaults.containsKey("requests")
                && yaml.node("modules", "requests").virtual()
                && !yaml.node("modules", "request-module").virtual()) {
            return false;
        }
        if (!"modules".equals(basePath)
                || !yaml.node("modules", "communications").virtual()) {
            return true;
        }
        for (String legacy : LEGACY_COMMUNICATION_MODULES) {
            if (!yaml.node("modules", legacy).virtual()) {
                return false;
            }
        }
        return true;
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value instanceof String string && Boolean.parseBoolean(string);
    }

    private Map<String, Object> discoverModuleDefaults() {
        LinkedHashMap<String, Object> defaults = new LinkedHashMap<>();
        try {
            java.util.ServiceLoader<ModuleProvider> loader = java.util.ServiceLoader.load(ModuleProvider.class, AppConfig.class.getClassLoader());
            for (java.util.ServiceLoader.Provider<ModuleProvider> provider : loader.stream().toList()) {
                Class<? extends ModuleProvider> type = provider.type();
                TensaModule annotation = type.getAnnotation(TensaModule.class);
                if (annotation == null) {
                    ua.co.tensa.Message.warn("Module provider has no @TensaModule metadata: " + type.getName());
                    continue;
                }
                defaults.put(annotation.id(), annotation.defaultEnabled());
            }
        } catch (Throwable failure) {
            ua.co.tensa.Message.warn("Module default discovery failed: " + failure.getClass().getSimpleName());
        }
        return defaults;
    }
}
