package ua.co.tensa.config.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Lightweight adapter over a YAML view. */
public interface YamlAdapter {
    String getString(String path, String def);
    boolean getBoolean(String path, boolean def);
    int getInt(String path, int def);
    long getLong(String path, long def);
    double getDouble(String path, double def);
    List<String> getStringList(String path);
    Set<String> getKeys(boolean deep);
    Set<String> childKeys(String path);
    boolean contains(String path);
    Map<String, Object> getSection(String path);
    List<Object> getList(String path);
}

