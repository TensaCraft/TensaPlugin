package ua.co.tensa.config.model;

import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import ua.co.tensa.Message;
import ua.co.tensa.config.model.ann.CfgKey;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reflection-based binder that maps fields annotated with @CfgKey to Configurate nodes.
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

    boolean writeMissingDefaults(CommentedConfigurationNode root) {
        boolean changed = false;

        for (Field field : fields) {
            CfgKey key = field.getAnnotation(CfgKey.class);
            String base = key.value();

            try {
                Object def = field.get(target);

                boolean allow = true;
                if (target instanceof ConfigBase cm) {
                    allow = cm.shouldWriteDefault(base, def, root);
                }
                if (!allow) {
                    continue;
                }

                if (def instanceof Map<?, ?> map) {
                    boolean mapChanged = writeMapDefaults(root, base, map);
                    if (mapChanged) {
                        changed = true;
                        setComment(root, base, key.comment());
                    }
                    continue;
                }

                CommentedConfigurationNode node = node(root, base);
                if (node.virtual() || node.raw() == null) {
                    node.set(copyValue(def));
                    changed = true;
                    setComment(root, base, key.comment());
                }
            } catch (IllegalAccessException | SerializationException e) {
                Message.warn("Config model default write failed for " + base + ": " + e.getMessage());
            }
        }

        return changed;
    }

    private boolean writeMapDefaults(CommentedConfigurationNode root, String base, Map<?, ?> map) {
        boolean changed = false;

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            String full = (base == null || base.isBlank()) ? key : base + "." + key;
            Object value = entry.getValue();

            if (value instanceof Map<?, ?> nested) {
                if (writeMapDefaults(root, full, nested)) {
                    changed = true;
                }
                continue;
            }

            CommentedConfigurationNode node = node(root, full);
            if (node.virtual() || node.raw() == null) {
                try {
                    node.set(copyValue(value));
                    changed = true;
                } catch (SerializationException e) {
                    Message.warn("Config map default write failed for " + full + ": " + e.getMessage());
                }
            }
        }

        return changed;
    }

    void loadFromYaml(CommentedConfigurationNode cfg) {
        for (Field field : fields) {
            CfgKey key = field.getAnnotation(CfgKey.class);
            String path = key.value();
            Class<?> type = field.getType();
            CommentedConfigurationNode node = node(cfg, path);

            try {
                if (type == String.class) {
                    String def = (String) field.get(target);
                    field.set(target, node.getString(def));
                    continue;
                }

                if (type == boolean.class || type == Boolean.class) {
                    boolean def = field.get(target) instanceof Boolean b && b;
                    field.set(target, node.virtual() ? def : node.getBoolean(def));
                    continue;
                }

                if (type == int.class || type == Integer.class) {
                    int def = field.get(target) instanceof Integer i ? i : 0;
                    field.set(target, node.virtual() ? def : node.getInt(def));
                    continue;
                }

                if (type == long.class || type == Long.class) {
                    long def = field.get(target) instanceof Long l ? l : 0L;
                    field.set(target, node.virtual() ? def : node.getLong(def));
                    continue;
                }

                if (type == double.class || type == Double.class) {
                    double def = field.get(target) instanceof Double d ? d : 0D;
                    field.set(target, node.virtual() ? def : node.getDouble(def));
                    continue;
                }

                if (List.class.isAssignableFrom(type)) {
                    @SuppressWarnings("unchecked")
                    List<String> def = (List<String>) field.get(target);
                    field.set(target, new ArrayList<>(node.getList(String.class, def == null ? List.of() : def)));
                    continue;
                }

                if (Map.class.isAssignableFrom(type)) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> def = (Map<String, Object>) field.get(target);

                    if (!node.virtual() && !node.childrenMap().isEmpty()) {
                        field.set(target, readSection(node));
                    } else if (def != null) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> copy = (Map<String, Object>) copyValue(def);
                        field.set(target, copy);
                    }
                    continue;
                }

                if (!node.virtual() && node.raw() != null) {
                    field.set(target, node.raw());
                }
            } catch (IllegalAccessException | SerializationException e) {
                Message.warn("Config model load failed for " + path + ": " + e.getMessage());
            }
        }
    }

    private Map<String, Object> readSection(CommentedConfigurationNode section) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<Object, ? extends CommentedConfigurationNode> entry : section.childrenMap().entrySet()) {
            out.put(String.valueOf(entry.getKey()), toJavaValue(entry.getValue()));
        }
        return out;
    }

    private Object toJavaValue(CommentedConfigurationNode node) {
        if (!node.childrenMap().isEmpty()) {
            return readSection(node);
        }
        if (!node.childrenList().isEmpty()) {
            List<Object> out = new ArrayList<>();
            for (CommentedConfigurationNode child : node.childrenList()) {
                out.add(toJavaValue(child));
            }
            return out;
        }
        return copyValue(node.raw());
    }

    private void setComment(CommentedConfigurationNode root, String path, String comment) {
        if (comment != null && !comment.isBlank()) {
            node(root, path).comment(comment);
        }
    }

    private CommentedConfigurationNode node(CommentedConfigurationNode root, String path) {
        if (path == null || path.isBlank()) {
            return root;
        }
        return root.node((Object[]) path.split("\\."));
    }

    private Object copyValue(Object value) {
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            for (Object item : list) {
                copy.add(copyValue(item));
            }
            return copy;
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
