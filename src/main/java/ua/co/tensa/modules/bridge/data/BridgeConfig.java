package ua.co.tensa.modules.bridge.data;

import ua.co.tensa.config.model.ConfigBase;
import ua.co.tensa.config.model.ann.CfgKey;

public class BridgeConfig extends ConfigBase {
    private static BridgeConfig instance;

    @CfgKey(value = "token", comment = "Shared bridge token. Leave empty to disable external bridge execution")
    public String token = "";

    @CfgKey(value = "use_velocity_secret", comment = "Accept the Velocity forwarding secret as a valid bridge token")
    public boolean useVelocitySecret = true;

    @CfgKey(value = "channel", comment = "Plugin messaging channel used for bridge traffic")
    public String channel = "tensa:exec";

    @CfgKey(value = "log", comment = "Log accepted bridge executions to console")
    public boolean log = true;

    @CfgKey(value = "allow_from", comment = "Allowed source server names. Leave empty to accept from any backend")
    public java.util.List<String> allowFrom = new java.util.ArrayList<>();

    private BridgeConfig() { super("bridge.yml"); }
    public static synchronized BridgeConfig get() { if (instance == null) instance = new BridgeConfig(); return instance; }
}
