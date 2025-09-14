package ua.co.tensa.modules;

import ua.co.tensa.Util;
import ua.co.tensa.commands.*;
import ua.co.tensa.config.Config;
import ua.co.tensa.modules.bridge.PMBridgeModule;
import ua.co.tensa.modules.chat.ChatModule;
import ua.co.tensa.modules.event.EventsModule;
import ua.co.tensa.modules.meta.UserMetaModule;
import ua.co.tensa.modules.playertime.PlayerTimeModule;
import ua.co.tensa.modules.rcon.manager.RconManagerModule;
import ua.co.tensa.modules.rcon.server.RconServerModule;
import ua.co.tensa.modules.requests.RequestsModule;
import ua.co.tensa.modules.text.TextReaderModule;

import java.util.LinkedHashMap;
import java.util.Map;

public class Modules {
    private static final Map<String, ModuleEntry> REGISTRY = new LinkedHashMap<>();

    static {
        REGISTRY.put("rcon-manager", RconManagerModule.ENTRY);
        REGISTRY.put("rcon-server", RconServerModule.ENTRY);
        REGISTRY.put("events-manager", EventsModule.ENTRY);
        REGISTRY.put("pm-bridge", PMBridgeModule.ENTRY);
        REGISTRY.put("request-module", RequestsModule.ENTRY);
        REGISTRY.put("player-time", PlayerTimeModule.ENTRY);
        REGISTRY.put("text-reader", TextReaderModule.ENTRY);
        REGISTRY.put("chat-manager", ChatModule.ENTRY);
        REGISTRY.put("user-meta", UserMetaModule.ENTRY);
    }

    public Modules() {
        ua.co.tensa.Message.info("TENSA loading modules...");
        Config.databaseInitializer();
        applyConfig();
        registerCommands();
    }

    public static void load() {
        new Modules();
    }

    public static void applyConfig() {
        for (Map.Entry<String, ModuleEntry> e : REGISTRY.entrySet()) {
            String id = e.getKey();
            ModuleEntry m = e.getValue();
            boolean desired = Config.getModules(id);
            if (desired && !m.isEnabled()) m.enable();
            if (!desired && m.isEnabled()) m.disable();
        }
    }

    public static void reloadAll() {
        for (ModuleEntry m : REGISTRY.values()) {
            if (m.isEnabled()) {
                try { m.reload(); } catch (Throwable t) { ua.co.tensa.Message.warn("Module reload failed: " + m.id() + " - " + t.getMessage()); }
            }
        }
    }

    // Snapshot view for info commands or admin tools
    public static java.util.Map<String, ModuleEntry> getEntries() {
        return java.util.Collections.unmodifiableMap(REGISTRY);
    }

    private void loadModules(String module) { /* deprecated: replaced by applyConfig() */ }

    private void registerCommands() {
        Util.registerCommand("tensareload", "treload", new ReloadCommand());
        Util.registerCommand("tensa", "tensahelp", new HelpCommand());
        Util.registerCommand("tensamodules", "tmodules", new ModulesCommand());
        Util.registerCommand("tpl", "tplugins", new PluginsCommand());
        Util.registerCommand("psend", "tpsend", new PlayerSendCommand());
        Util.registerCommand("tparse", "tph", new PlaceholderParseCommand());
        Util.registerCommand("tinfo", "tinfo", new TensaInfoCommand());
        // Debug for PM bridge
        Util.registerCommand("tpmdebug", "tpmdbg", new ua.co.tensa.modules.bridge.PMBridgeDebugCommand());
    }

    // wrappers replaced by module-provided ENTRY
}
