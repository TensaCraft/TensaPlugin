package ua.co.tensa.modules.bridge.data;

import ua.co.tensa.config.data.BaseYAMLConfig;

public class BridgeYAML extends BaseYAMLConfig {
    private static BridgeYAML instance;

    private BridgeYAML() {
        super("bridge.yml");
    }

    public static BridgeYAML getInstance() {
        if (instance == null) instance = new BridgeYAML();
        return instance;
    }

    @Override
    protected void populateConfigFile() {
        yamlFile.setHeader("TENSA PM Bridge configuration");
        setConfigValue("token", "");
        setConfigValue("use_velocity_secret", true);
        setConfigValue("channel", "tensa:exec");
        setConfigValue("log", true);
    }
}
