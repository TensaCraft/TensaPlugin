package ua.co.tensa.config.core;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Thin abstraction over YAML configuration access.
 * All paths use dot-notation, e.g. "global.command".
 */
public interface ConfigAdapter {
    String getString(String path, String def);
    boolean getBoolean(String path, boolean def);
    int getInt(String path, int def);
    long getLong(String path, long def);
    double getDouble(String path, double def);
    List<String> getStringList(String path);

    /**
     * Top-level keys or all keys if deep=true.
     */
    Set<String> getKeys(boolean deep);

    /**
     * Direct child keys of a section. If path is null/empty, returns root keys.
     */
    Set<String> childKeys(String path);

    boolean contains(String path);

    /**
     * Returns a deep map of a section (empty if missing).
     * If path is null/empty, returns the whole root map.
     */
    Map<String, Object> getSection(String path);

    /**
     * Returns a list at given path (empty if missing or non-list).
     */
    List<Object> getList(String path);
}
