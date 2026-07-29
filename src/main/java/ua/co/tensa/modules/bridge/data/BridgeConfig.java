package ua.co.tensa.modules.bridge.data;

import ua.co.tensa.config.model.ConfigBase;
import ua.co.tensa.config.model.ann.CfgKey;

public class BridgeConfig extends ConfigBase {
    private static BridgeConfig instance;

    @CfgKey(
            value = "compatibility_mode",
            comment = "Explicitly enable the legacy token:command protocol; the module is disabled by default"
    )
    public boolean compatibilityMode = false;

    @CfgKey(value = "token", comment = "Dedicated non-empty token used only by this compatibility bridge")
    public String token = "";

    @CfgKey(value = "channel", comment = "Plugin messaging channel used for bridge traffic")
    public String channel = "tensa:exec";

    @CfgKey(value = "log", comment = "Log accepted bridge executions to console")
    public boolean log = true;

    @CfgKey(
            value = "allow_from",
            comment = "Required exact source server names; wildcard and all are rejected"
    )
    public java.util.List<String> allowFrom = new java.util.ArrayList<>();

    private BridgeConfig() { super("bridge.yml"); }
    public static synchronized BridgeConfig get() { if (instance == null) instance = new BridgeConfig(); return instance; }
}
