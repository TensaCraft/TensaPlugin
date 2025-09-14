package ua.co.tensa.modules.rcon.data;

import ua.co.tensa.config.data.BaseYAMLConfig;

import java.io.File;

public class RconServerYAML extends BaseYAMLConfig {

    private static RconServerYAML instance;

    private RconServerYAML() {
        super("rcon" + File.separator + "rcon-server.yml");
    }

    public static RconServerYAML getInstance() {
        if (instance == null) {
            instance = new RconServerYAML();
        }
        return instance;
    }

    @Override
    protected void populateConfigFile() {
        yamlFile.setHeader(
                "Rcon server settings\n" +
                        "To receive RCON server connection notifications: velocityutil.rcon.notify"
        );

        yamlFile.setComment("port", "Rcon port");
        setConfigValue("port", 25570);

        yamlFile.setComment("password", "Rcon password");
        setConfigValue("password", "password");

        yamlFile.setComment("colored", "The response is colored or not");
        setConfigValue("colored", true);
    }
}
