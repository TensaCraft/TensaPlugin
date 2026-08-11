package ua.co.tensa.modules;

import ua.co.tensa.Tensa;
import ua.co.tensa.Util;
import ua.co.tensa.commands.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

public class Modules {
    private static final Map<String, ModuleEntry> REGISTRY = new LinkedHashMap<>();
    private static final Object RELOAD_LOCK = new Object();

    public enum ReloadResult {
        RELOADED,
        RESTART_REQUIRED,
        NOT_FOUND,
        DISABLED,
        FAILED
    }

    public Modules() {
        REGISTRY.clear();
        ua.co.tensa.Message.info("Tensa loading modules...");
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
        synchronizeModules(false);
        registerCommands();
    }

    public static void load() {
        new Modules();
    }

    public static void applyConfig() {
        synchronizeModules(false);
    }

    public static java.util.List<String> reloadAll() {
        return synchronizeModules(true);
    }

    /** Apply config states (enable/disable) and soft-reload enabled modules. */
    public static java.util.List<String> refresh() {
        return synchronizeModules(true);
    }

    // Snapshot view for info commands or admin tools
    public static java.util.Map<String, ModuleEntry> getEntries() {
        synchronized (REGISTRY) {
            return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(REGISTRY));
        }
    }

    public static ReloadResult reloadModule(String moduleId) {
        synchronized (RELOAD_LOCK) {
            try {
                if (Tensa.config == null) {
                    Tensa.config = new ua.co.tensa.config.Config();
                } else {
                    Tensa.config.reload();
                }
            } catch (Throwable throwable) {
                ua.co.tensa.Message.warn("Targeted module reload rejected by root config validation: "
                        + throwable.getClass().getSimpleName());
                return ReloadResult.FAILED;
            }
            return reloadModule(REGISTRY, moduleId, Tensa.config::isModuleEnabled);
        }
    }

    static ReloadResult reloadModule(Map<String, ? extends ModuleEntry> registry, String moduleId) {
        return reloadModule(registry, moduleId, id -> {
            ModuleEntry module = registry.get(id);
            return module != null && module.isEnabled();
        });
    }

    static ReloadResult reloadModule(
            Map<String, ? extends ModuleEntry> registry,
            String moduleId,
            Predicate<String> desiredEnabled
    ) {
        if (moduleId == null || moduleId.isBlank()) {
            return ReloadResult.NOT_FOUND;
        }
        String normalizedId = moduleId.trim().toLowerCase(java.util.Locale.ROOT);
        ModuleEntry module = registry.get(normalizedId);
        if (module == null) {
            return ReloadResult.NOT_FOUND;
        }
        boolean desired = module.required() || desiredEnabled.test(normalizedId);
        if (!desired) {
            if (module.isEnabled()) {
                module.disable();
            }
            return ReloadResult.DISABLED;
        }
        if (!module.isEnabled()) {
            try {
                module.enable();
                return module.isEnabled() ? ReloadResult.RELOADED : ReloadResult.FAILED;
            } catch (Throwable throwable) {
                ua.co.tensa.Message.warn("Module enable failed during targeted reload: "
                        + module.id() + " - " + throwable.getClass().getSimpleName());
                return ReloadResult.FAILED;
            }
        }
        try {
            if (!module.tryReload()) {
                return ReloadResult.FAILED;
            }
            return module.reloadRequiresRestart()
                    ? ReloadResult.RESTART_REQUIRED
                    : ReloadResult.RELOADED;
        } catch (Throwable throwable) {
            ua.co.tensa.Message.warn("Module reload failed: " + module.id() + " - " + throwable.getMessage());
            return ReloadResult.FAILED;
        }
    }

    public static void disableAll() {
        for (ModuleEntry module : REGISTRY.values()) {
            if (module.isEnabled()) {
                try {
                    module.disable();
                } catch (Throwable t) {
                    ua.co.tensa.Message.warn("Module disable failed: " + module.id() + " - " + t.getMessage());
                }
            }
        }
    }

    // no-op: modules are applied via applyConfig()

    private void registerCommands() {
        Util.registerCommand("tensareload", "treload", new ReloadCommand());
        Util.registerCommand("tensa", "", new HelpCommand());
        Util.registerCommand("tensahelp", "", new HelpCommand());
        Util.registerCommand("tensamodules", "tmodules", new ModulesCommand());
        Util.registerCommand("tpl", "tplugins", new PluginsCommand());
        Util.registerCommand("psend", "tpsend", new PlayerSendCommand());
        Util.registerCommand("tparse", "tph", new PlaceholderParseCommand());
        Util.registerCommand("tensainfo", "tinfo", new TensaInfoCommand());
    }

    private static java.util.List<String> synchronizeModules(boolean reloadEnabled) {
        java.util.List<String> failures = new java.util.ArrayList<>();
        for (Map.Entry<String, ModuleEntry> entry : REGISTRY.entrySet()) {
            String id = entry.getKey();
            ModuleEntry module = entry.getValue();
            boolean desired = module.required() || Tensa.config != null && Tensa.config.isModuleEnabled(id);

            if (!desired) {
                if (module.isEnabled()) {
                    module.disable();
                }
                continue;
            }

            if (!module.isEnabled()) {
                module.enable();
                if (!module.isEnabled()) {
                    failures.add(id);
                }
                continue;
            }

            if (reloadEnabled) {
                try {
                    if (!module.tryReload() || module.reloadRequiresRestart()) {
                        failures.add(id);
                    }
                } catch (Throwable t) {
                    ua.co.tensa.Message.warn("Module reload failed: " + module.id() + " - " + t.getMessage());
                    failures.add(id);
                }
            }
        }
        return java.util.List.copyOf(failures);
    }

    // wrappers replaced by module-provided ENTRY
}
