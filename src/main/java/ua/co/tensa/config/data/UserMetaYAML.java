package ua.co.tensa.config.data;

public class UserMetaYAML extends BaseYAMLConfig {
    private static UserMetaYAML instance;

    private UserMetaYAML() {
        super("user_meta/config.yml");
    }

    public static synchronized UserMetaYAML getInstance() {
        if (instance == null) instance = new UserMetaYAML();
        return instance;
    }

    @Override
    protected void populateConfigFile() {
        setConfigValue("storage.type", "database"); // database | file | memory
        setConfigValue("storage.file", "user_meta/data.yml");
        setConfigValue("default_persist", true);
    }
}

