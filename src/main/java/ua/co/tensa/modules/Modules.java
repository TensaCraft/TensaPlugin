package ua.co.tensa.modules;

import ua.co.tensa.Tensa;
import ua.co.tensa.Util;
import ua.co.tensa.commands.*;

import java.util.LinkedHashMap;
import java.util.Map;

public class Modules {
    private static final Map<String, ModuleEntry> REGISTRY = new LinkedHashMap<>();

    public Modules() {
        ua.co.tensa.Message.info("TENSA loading modules...");
        // Auto-discover modules via ServiceLoader
        try {
            java.util.ServiceLoader<ModuleProvider> loader = java.util.ServiceLoader.load(ModuleProvider.class, Modules.class.getClassLoader());
            int count = 0;
            for (ModuleProvider p : loader) {
                try {
                    ModuleEntry e = p.entry();
                    if (e != null) {
                        REGISTRY.put(p.id(), e);
                        count++;
                    }
                } catch (Throwable t) {
                    ua.co.tensa.Message.warn("Failed to register module provider: " + p.getClass().getName() + " - " + t.getMessage());
                }
            }
            ua.co.tensa.Message.info("Discovered modules: " + count);
        } catch (Throwable t) {
            ua.co.tensa.Message.warn("Module discovery failed: " + t.getMessage());
        }
        ua.co.tensa.config.DatabaseInitializer initializer;
        if (Tensa.config != null && Tensa.config.databaseEnable()) {
            Tensa.database = new ua.co.tensa.config.Database();
            if (Tensa.database.connect()) {
                initializer = new ua.co.tensa.config.DatabaseInitializer(Tensa.database);
                initializer.initializeTables();
            }
        }
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
            boolean desired = Tensa.config != null && Tensa.config.isModuleEnabled(id);
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

    /** Apply config states (enable/disable) and soft-reload enabled modules. */
    public static void refresh() {
        applyConfig();
        reloadAll();
    }

    // Snapshot view for info commands or admin tools
    public static java.util.Map<String, ModuleEntry> getEntries() {
        return java.util.Collections.unmodifiableMap(REGISTRY);
    }

    // no-op: modules are applied via applyConfig()

    private void registerCommands() {
        Util.registerCommand("tensareload", "treload", new ReloadCommand());
        Util.registerCommand("tensa", "tensahelp", new HelpCommand());
        Util.registerCommand("tensamodules", "tmodules", new ModulesCommand());
        Util.registerCommand("tpl", "tplugins", new PluginsCommand());
        Util.registerCommand("psend", "tpsend", new PlayerSendCommand());
        Util.registerCommand("tparse", "tph", new PlaceholderParseCommand());
        Util.registerCommand("tinfo", "tinfo", new TensaInfoCommand());
    }

    // wrappers replaced by module-provided ENTRY
}
