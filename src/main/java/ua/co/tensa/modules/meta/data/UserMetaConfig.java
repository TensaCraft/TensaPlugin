package ua.co.tensa.modules.meta.data;

import ua.co.tensa.config.model.ConfigBase;
import ua.co.tensa.config.model.ann.CfgKey;

/**
 * Typed model for user meta configuration.
 */
public class UserMetaConfig extends ConfigBase {
    private static UserMetaConfig instance;

    @CfgKey(value = "storage.type", comment = "Storage backend for user meta: database, file or memory")
    public String storageType = "database"; // database | file | memory

    @CfgKey(value = "storage.file", comment = "Relative file path used when storage.type = file")
    public String storageFile = "user_meta/data.yml";

    @CfgKey(value = "default_persist", comment = "Persist values by default unless a command overrides it")
    public boolean defaultPersist = true;

    private UserMetaConfig() {
        super("user_meta/config.yml");
    }

    public static synchronized UserMetaConfig get() {
        if (instance == null) instance = new UserMetaConfig();
        return instance;
    }
}

