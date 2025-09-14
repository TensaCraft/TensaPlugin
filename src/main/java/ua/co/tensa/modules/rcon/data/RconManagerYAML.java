package ua.co.tensa.modules.rcon.data;

import ua.co.tensa.config.data.BaseYAMLConfig;

import java.io.File;

public class RconManagerYAML extends BaseYAMLConfig {

    private static RconManagerYAML instance;

    private RconManagerYAML() {
        super("rcon" + File.separator + "rcon-manager.yml");
    }

    public static RconManagerYAML getInstance() {
        if (instance == null) {
            instance = new RconManagerYAML();
        }
        return instance;
    }

    @Override
    protected void populateConfigFile() {
        yamlFile.setHeader(
                "Rcon servers\n" +
                        "To allow the use of a separate server for a player, use permission:\n" +
                        "tensa.rcon.serve_name\n" +
                        "Examples: tensa.rcon.lobby, tensa.rcon.vanilla"
        );

        setConfigValue("servers.lobby.ip", "0.0.0.0");
        setConfigValue("servers.lobby.port", 25566);
        setConfigValue("servers.lobby.pass", "rcon password");

        yamlFile.setComment("tab-complete-list", "List of rcon server command arguments");
        setConfigValue("tab-complete-list", new String[]{
                "alert",
                "list",
                "tps"
        });
    }
}
