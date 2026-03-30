package ua.co.tensa.config.model;

import org.simpleyaml.configuration.ConfigurationSection;
import org.simpleyaml.configuration.file.YamlConfiguration;
import org.simpleyaml.configuration.file.YamlFile;
import ua.co.tensa.Message;
import ua.co.tensa.config.model.ann.CfgKey;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
            for (Field field : c.getDeclaredFields()) {
                if (field.isAnnotationPresent(CfgKey.class)) {
                    field.setAccessible(true);
                    fields.add(field);
                }
            }
        }
    }

    boolean writeMissingDefaults(YamlFile yaml) {
        boolean changed = false;

        for (Field field : fields) {
            CfgKey key = field.getAnnotation(CfgKey.class);
            String base = key.value();

            try {
                Object def = field.get(target);

                boolean allow = true;
                if (target instanceof ConfigBase cm) {
                    allow = cm.shouldWriteDefault(base, def, yaml);
                }
                if (!allow) {
                    continue;
                }

                if (def instanceof Map<?, ?> map) {
                    boolean mapChanged = writeMapDefaults(yaml, base, map);
                    if (mapChanged) {
                        changed = true;
                        if (!key.comment().isBlank()) {
                            yaml.setComment(base, key.comment());
                        }
                    }
                    continue;
                }

                if (!yaml.contains(base)) {
                    yaml.set(base, copyValue(def));
                    changed = true;

                    if (!key.comment().isBlank()) {
                        yaml.setComment(base, key.comment());
                    }
                }
            } catch (IllegalAccessException e) {
                Message.warn("Config model default write failed for " + base + ": " + e.getMessage());
            }
        }

        return changed;
    }

    private boolean writeMapDefaults(YamlFile yaml, String base, Map<?, ?> map) {
        boolean changed = false;

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            String full = (base == null || base.isBlank()) ? key : base + "." + key;
            Object value = entry.getValue();

            if (value instanceof Map<?, ?> nested) {
                if (writeMapDefaults(yaml, full, nested)) {
                    changed = true;
                }
                continue;
            }

            if (!yaml.contains(full)) {
                yaml.set(full, copyValue(value));
                changed = true;
            }
        }

        return changed;
    }

    void loadFromYaml(YamlConfiguration cfg) {
        for (Field field : fields) {
            CfgKey key = field.getAnnotation(CfgKey.class);
            String path = key.value();
            Class<?> type = field.getType();

            try {
                if (type == String.class) {
                    String def = (String) field.get(target);
                    field.set(target, cfg.getString(path, def));
                    continue;
                }

                if (type == boolean.class || type == Boolean.class) {
                    boolean def = field.get(target) instanceof Boolean b ? b : false;
                    field.set(target, cfg.contains(path) ? cfg.getBoolean(path) : def);
                    continue;
                }

                if (type == int.class || type == Integer.class) {
                    int def = field.get(target) instanceof Integer i ? i : 0;
                    field.set(target, cfg.contains(path) ? cfg.getInt(path) : def);
                    continue;
                }

                if (type == long.class || type == Long.class) {
                    long def = field.get(target) instanceof Long l ? l : 0L;
                    field.set(target, cfg.contains(path) ? cfg.getLong(path) : def);
                    continue;
                }

                if (type == double.class || type == Double.class) {
                    double def = field.get(target) instanceof Double d ? d : 0D;
                    field.set(target, cfg.contains(path) ? cfg.getDouble(path) : def);
                    continue;
                }

                if (List.class.isAssignableFrom(type)) {
                    @SuppressWarnings("unchecked")
                    List<String> def = (List<String>) field.get(target);

                    if (cfg.contains(path)) {
                        List<String> list = cfg.getStringList(path);
                        field.set(target, list == null ? new ArrayList<>() : new ArrayList<>(list));
                    } else {
                        field.set(target, def == null ? new ArrayList<>() : new ArrayList<>(def));
                    }
                    continue;
                }

                if (Map.class.isAssignableFrom(type)) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> def = (Map<String, Object>) field.get(target);

                    ConfigurationSection section = cfg.getConfigurationSection(path);
                    if (section != null) {
                        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
                        readSection(section, map);
                        field.set(target, map);
                    } else if (def != null) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> copy = (Map<String, Object>) copyValue(def);
                        field.set(target, copy);
                    }
                    continue;
                }

                if (cfg.contains(path)) {
                    field.set(target, cfg.get(path));
                }
            } catch (IllegalAccessException e) {
                Message.warn("Config model load failed for " + path + ": " + e.getMessage());
            }
        }
    }

    private void readSection(ConfigurationSection section, Map<String, Object> out) {
        for (String child : section.getKeys(false)) {
            if (section.isConfigurationSection(child)) {
                ConfigurationSection childSection = section.getConfigurationSection(child);
                LinkedHashMap<String, Object> nested = new LinkedHashMap<>();
                if (childSection != null) {
                    readSection(childSection, nested);
                }
                out.put(child, nested);
            } else {
                out.put(child, copyValue(section.get(child)));
            }
        }
    }

    private Object copyValue(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }

        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), copyValue(entry.getValue()));
            }
            return copy;
        }

        return value;
    }
}