package ua.co.tensa.modules.rcon.manager;

import ua.co.tensa.modules.AbstractModule;
import ua.co.tensa.modules.ModuleEntry;
import ua.co.tensa.modules.rcon.data.RconManagerConfig;

import java.util.ArrayList;
import java.util.List;

public class RconManagerModule {

    private static final ModuleEntry IMPL = new AbstractModule(
            "rcon-manager", "Rcon Manager") {
        @Override protected void onEnable() {
            try {
                RconManagerConfig.get().reloadCfg();
                ua.co.tensa.modules.AbstractModule.registerCommand("rcon", "trcon", new RconManagerCommand());
            } catch (Exception e) {
                ua.co.tensa.Message.error("RconManager onEnable failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            }
        }
        @Override protected void onReload() { RconManagerConfig.get().reloadCfg(); }
        @Override protected void onDisable() {
            ua.co.tensa.modules.AbstractModule.unregisterCommands("vurcon", "rcon", "velocityrcon");
        }
    };

    public static final ModuleEntry ENTRY = IMPL;

    public static boolean serverIs(String server) {
        String ip = RconManagerConfig.get().ip(server, "");
        return !ip.isEmpty();
    }

    public static List<String> getServers() {
        return new ArrayList<>(RconManagerConfig.get().serverKeys());
    }

    public static Integer getPort(String server) {
        return RconManagerConfig.get().port(server, 25575);
    }

    public static String getIP(String server) {
        return RconManagerConfig.get().ip(server, "127.0.0.1");
    }

    public static String getPass(String server) {
        return RconManagerConfig.get().pass(server, "");
    }

    public static ArrayList<String> getCommandArgs() {
        return new ArrayList<>(RconManagerConfig.get().tabComplete);
    }

    public static void enable() { IMPL.enable(); }
    public static void disable() { IMPL.disable(); }
}
