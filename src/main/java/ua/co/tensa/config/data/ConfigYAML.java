package ua.co.tensa.config.data;

public class ConfigYAML extends BaseYAMLConfig {

    private static ConfigYAML instance;

    private ConfigYAML() {
        super("config.yml");
    }

    public static ConfigYAML getInstance() {
        if (instance == null) {
            instance = new ConfigYAML();
        }
        return instance;
    }

    @Override
    protected void populateConfigFile() {
        yamlFile.setHeader("General Configuration File");

        setConfigValue("language", "en");

        yamlFile.setComment("use_uuid", "Use UUID to store player data. If false, the nickname will be used");
        setConfigValue("use_uuid", false);

        populateModules();
        populateDatabase();
    }

    private void populateModules() {
        yamlFile.setBlankLine("modules");

        yamlFile.setComment("modules.rcon-manager", "Rcon manager. Allows sending RCON commands to other servers");
        setConfigValue("modules.rcon-manager", true);

        yamlFile.setComment("modules.rcon-server", "Rcon server. Enables Velocity RCON server to run");
        setConfigValue("modules.rcon-server", true);


        // Removed php-runner and bash-runner modules
        yamlFile.setComment("modules.pm-bridge", "PluginMessage bridge: send commands from backend via plugin messaging");
        setConfigValue("modules.pm-bridge", false);

        yamlFile.setComment("modules.events-manager", "Events manager. Allows you to use an event handler");
        setConfigValue("modules.events-manager", true);

        yamlFile.setComment("modules.request-module", "Request module. Allows you to perform HTTP requests");
        setConfigValue("modules.request-module", true);

        yamlFile.setComment("modules.player-time", "Player Time module. Keeps statistics of the player's playing time");
        setConfigValue("modules.player-time", true);

        yamlFile.setComment("modules.text-reader", "Text Reader module. Allows reading and outputting text files and chat");
        setConfigValue("modules.text-reader", true);

        yamlFile.setComment("modules.chat-manager", "Chat manager. Allows you to manage chat");
        setConfigValue("modules.chat-manager", true);

        yamlFile.setComment("modules.user-meta", "User meta. Allows us to keep the purpose");
        setConfigValue("modules.user-meta", true);
    }

    private void populateDatabase() {
        yamlFile.setBlankLine("database");
        yamlFile.setComment("database.type", "only \"mysql\"");
        setConfigValue("database.enable", false);
        setConfigValue("database.type", "mysql");
        setConfigValue("database.name", "server");
        setConfigValue("database.user", "root");
        setConfigValue("database.password", "password");
        setConfigValue("database.host", "localhost");
        setConfigValue("database.port", 3306);
        setConfigValue("database.use_ssl", false);
        setConfigValue("database.table_prefix", "tensa_");
    }
}
