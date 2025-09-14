package ua.co.tensa.modules.rcon.manager;

import ua.co.tensa.config.data.RconManagerYAML;
import ua.co.tensa.modules.AbstractModule;
import ua.co.tensa.modules.ModuleEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;


public class RconManagerModule {

    private static final ModuleEntry IMPL = new AbstractModule(
            "rcon-manager", "Rcon Manager") {
        @Override protected void onEnable() {
            // Ensure config file exists and populated
            ua.co.tensa.modules.AbstractModule.ensureConfig(RconManagerYAML.getInstance());
            ua.co.tensa.modules.AbstractModule.registerCommand("rcon", "", new RconManagerCommand());
            ua.co.tensa.Message.info("Rcon Manager module enabled");
        }
        @Override protected void onDisable() {
            ua.co.tensa.modules.AbstractModule.unregisterCommands("vurcon", "rcon", "velocityrcon");
        }
    };

    public static final ModuleEntry ENTRY = IMPL;


    public static boolean serverIs(String server) {
        return RconManagerYAML.getInstance().adapter().contains("servers." + server);
    }

	public static List<String> getServers() {
		Set<String> keys = RconManagerYAML.getInstance().adapter().childKeys("servers");
		return new ArrayList<>(keys);
	}

	public static Integer getPort(String server) {
		return RconManagerYAML.getInstance().adapter().getInt("servers." + server + ".port", 25575);
	}

	public static String getIP(String server) {
		return RconManagerYAML.getInstance().adapter().getString("servers." + server + ".ip", "127.0.0.1");
	}

	public static String getPass(String server) {
		return RconManagerYAML.getInstance().adapter().getString("servers." + server + ".pass", "");
	}

    public static ArrayList<String> getCommandArgs() {
        ArrayList<String> args = new ArrayList<>();
        var list = RconManagerYAML.getInstance().adapter().getStringList("tab-complete-list");
        args.addAll(list);
        return args;
    }

    public static void enable() { IMPL.enable(); }
    public static void disable() { IMPL.disable(); }
}
