package ua.co.tensa.modules.bridge.data;

import ua.co.tensa.config.model.ConfigModelBase;
import ua.co.tensa.config.model.ann.CfgKey;

public class BridgeConfig extends ConfigModelBase {
    private static BridgeConfig instance;

    @CfgKey("token")
    public String token = "";

    @CfgKey("use_velocity_secret")
    public boolean useVelocitySecret = true;

    @CfgKey("channel")
    public String channel = "tensa:exec";

    @CfgKey("log")
    public boolean log = true;

    @CfgKey("allow_from")
    public java.util.List<String> allowFrom = new java.util.ArrayList<>();

    private BridgeConfig() { super("bridge.yml"); }
    public static synchronized BridgeConfig get() { if (instance == null) instance = new BridgeConfig(); return instance; }
}
