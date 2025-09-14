package ua.co.tensa.config.core;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ConfigAdapter {
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
    java.util.List<Object> getList(String path);
}
