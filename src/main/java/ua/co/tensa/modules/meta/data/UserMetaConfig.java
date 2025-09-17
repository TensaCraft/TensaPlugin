package ua.co.tensa.modules.meta.data;

import ua.co.tensa.config.model.ConfigModelBase;
import ua.co.tensa.config.model.ann.CfgKey;

/**
 * Typed model for user meta configuration.
 */
public class UserMetaConfig extends ConfigModelBase {
    private static UserMetaConfig instance;

    @CfgKey("storage.type")
    public String storageType = "database"; // database | file | memory

    @CfgKey("storage.file")
    public String storageFile = "user_meta/data.yml";

    @CfgKey("default_persist")
    public boolean defaultPersist = true;

    private UserMetaConfig() {
        super("user_meta/config.yml");
    }

    public static synchronized UserMetaConfig get() {
        if (instance == null) instance = new UserMetaConfig();
        return instance;
    }
}

