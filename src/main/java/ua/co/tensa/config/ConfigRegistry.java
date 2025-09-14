package ua.co.tensa.config;

import ua.co.tensa.Message;
import ua.co.tensa.config.data.BaseYAMLConfig;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Lightweight registry for YAML configs to allow bulk operations
 * like reloadAll() or saveAll() across modules. Optional utility.
 */
public final class ConfigRegistry {
    private static final Set<BaseYAMLConfig> REGISTRY = Collections.newSetFromMap(new WeakHashMap<>());

    private ConfigRegistry() {}

    public static void register(BaseYAMLConfig cfg) {
        if (cfg != null) REGISTRY.add(cfg);
    }

    public static void reloadAll() {
        int count = 0;
        for (BaseYAMLConfig cfg : REGISTRY) {
            if (cfg != null) {
                cfg.reload();
                count++;
            }
        }
        Message.info("Reloaded configs: " + count);
    }

    public static void saveAll() {
        int count = 0;
        for (BaseYAMLConfig cfg : REGISTRY) {
            if (cfg != null) {
                cfg.save();
                count++;
            }
        }
        Message.info("Saved configs: " + count);
    }
}
