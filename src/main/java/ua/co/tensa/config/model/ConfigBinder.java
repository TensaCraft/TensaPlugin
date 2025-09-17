package ua.co.tensa.config.model;

import org.simpleyaml.configuration.file.YamlConfiguration;
import org.simpleyaml.configuration.file.YamlFile;
import ua.co.tensa.Message;
import ua.co.tensa.config.model.ann.CfgKey;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reflection-based binder that maps fields annotated with @CfgKey
 * to values in YAML, writing defaults when missing.
 */
final class ConfigBinder {
    private final Object target;
    private final List<Field> fields = new ArrayList<>();

    ConfigBinder(Object target) {
        this.target = target;
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isAnnotationPresent(CfgKey.class)) {
                    f.setAccessible(true);
                    fields.add(f);
                }
            }
        }
    }

    void writeMissingDefaults(YamlFile yaml) {
        for (Field f : fields) {
            CfgKey k = f.getAnnotation(CfgKey.class);
            String base = k.value();
            try {
                Object def = f.get(target);
                boolean allow = true;
                if (target instanceof ConfigBase cm) {
                    allow = cm.shouldWriteDefault(base, def, yaml);
                }
                if (!allow) continue;

                if (def instanceof java.util.Map<?,?> map) {
                    // write nested keys recursively
                    writeMapDefaults(yaml, base, map);
                } else if (!yaml.contains(base)) {
                    if (!k.comment().isBlank()) yaml.setComment(base, k.comment());
                    yaml.set(base, def);
                }
            } catch (IllegalAccessException e) {
                Message.warn("Config model default write failed for " + base + ": " + e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void writeMapDefaults(YamlFile yaml, String base, java.util.Map<?,?> map) {
        for (var e : map.entrySet()) {
            String key = String.valueOf(e.getKey());
            String full = (base == null || base.isBlank()) ? key : base + "." + key;
            Object val = e.getValue();
            if (val instanceof java.util.Map<?,?> m2) {
                writeMapDefaults(yaml, full, (java.util.Map<?,?>) m2);
            } else {
                if (!yaml.contains(full)) yaml.set(full, val);
            }
        }
    }

    void loadFromYaml(YamlConfiguration cfg) {
        for (Field f : fields) {
            CfgKey k = f.getAnnotation(CfgKey.class);
            String path = k.value();
            Class<?> t = f.getType();
            try {
                if (t == String.class) {
                    String def = (String) f.get(target);
                    String val = cfg.getString(path, def);
                    f.set(target, val);
                } else if (t == boolean.class || t == Boolean.class) {
                    boolean cur = (f.get(target) instanceof Boolean b) ? b : false;
                    boolean val = cfg.contains(path) ? cfg.getBoolean(path) : cur;
                    f.set(target, val);
                } else if (t == int.class || t == Integer.class) {
                    int def = (f.get(target) instanceof Integer i) ? i : 0;
                    f.set(target, cfg.getInt(path, def));
                } else if (t == long.class || t == Long.class) {
                    long def = (f.get(target) instanceof Long l) ? l : 0L;
                    f.set(target, cfg.getLong(path, def));
                } else if (t == double.class || t == Double.class) {
                    double def = (f.get(target) instanceof Double d) ? d : 0D;
                    f.set(target, cfg.getDouble(path, def));
                } else if (List.class.isAssignableFrom(t)) {
                    @SuppressWarnings("unchecked")
                    List<String> def = (List<String>) f.get(target);
                    List<String> list = cfg.getStringList(path);
                    f.set(target, (list == null || list.isEmpty()) && def != null ? def : list);
                } else if (Map.class.isAssignableFrom(t)) {
                    org.simpleyaml.configuration.ConfigurationSection sec = cfg.getConfigurationSection(path);
                    if (sec != null) {
                        java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
                        for (String child : sec.getKeys(false)) {
                            if (sec.isConfigurationSection(child)) {
                                // shallow map of child section (no deep flattening)
                                org.simpleyaml.configuration.ConfigurationSection childSec = sec.getConfigurationSection(child);
                                m.put(child, childSec == null ? java.util.Map.of() : new java.util.LinkedHashMap<>(childSec.getMapValues(false)));
                            } else {
                                m.put(child, sec.get(child));
                            }
                        }
                        f.set(target, m);
                    }
                } else {
                    if (cfg.contains(path)) f.set(target, cfg.get(path));
                }
            } catch (IllegalAccessException e) {
                Message.warn("Config model load failed for " + path + ": " + e.getMessage());
            }
        }
    }
}
