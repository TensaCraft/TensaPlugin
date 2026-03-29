package ua.co.tensa.modules.rcon.data;

import ua.co.tensa.config.model.ConfigBase;
import ua.co.tensa.config.model.ann.CfgKey;

public class RconServerConfig extends ConfigBase {
    private static RconServerConfig instance;

    @CfgKey(value = "port", comment = "TCP port for the built-in RCON listener") public int port = 25570;
    @CfgKey(value = "password", comment = "Password required to authenticate to the built-in RCON listener") public String password = "password";
    @CfgKey(value = "colored", comment = "Preserve color codes in RCON responses when possible") public boolean colored = true;
    @CfgKey(value = "debug", comment = "Print verbose RCON protocol debug logs") public boolean debug = false;
    @CfgKey(value = "log-errors", comment = "Log RCON command execution exceptions to console") public boolean logErrors = false;

    private RconServerConfig() { super("rcon/rcon-server.yml"); }
    public static synchronized RconServerConfig get() { if (instance == null) instance = new RconServerConfig(); return instance; }
}

