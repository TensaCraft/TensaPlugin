package ua.co.tensa.modules.rcon.data;

import ua.co.tensa.config.model.ConfigBase;
import ua.co.tensa.config.model.ann.CfgKey;

public class RconServerConfig extends ConfigBase {
    private static RconServerConfig instance;

    @CfgKey("port") public int port = 25570;
    @CfgKey("password") public String password = "password";
    @CfgKey("colored") public boolean colored = true;
    @CfgKey("debug") public boolean debug = false;
    @CfgKey("log-errors") public boolean logErrors = false;

    private RconServerConfig() { super("rcon/rcon-server.yml"); }
    public static synchronized RconServerConfig get() { if (instance == null) instance = new RconServerConfig(); return instance; }
}

