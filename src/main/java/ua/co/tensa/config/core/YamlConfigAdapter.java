package ua.co.tensa.config.core;

import org.simpleyaml.configuration.ConfigurationSection;
import org.simpleyaml.configuration.file.YamlConfiguration;

import java.util.*;

/**
 * Adapter backed by SimpleYAML YamlConfiguration.
 * Safe towards null/empty paths and trims leading/trailing dots.
 */
public record YamlConfigAdapter(YamlConfiguration cfg) implements ConfigAdapter {

    // Normalize a path: trim, remove leading/trailing dots.
    private static String norm(String path) {
        if (path == null) return "";
        String p = path.trim();
        while (p.startsWith(".")) p = p.substring(1);
        while (p.endsWith(".")) p = p.substring(0, p.length() - 1);
        return p;
    }

    private ConfigurationSection sectionOrRoot(String path) {
        if (cfg == null) return null;
        String p = norm(path);
        if (p.isEmpty()) return cfg; // root
        return cfg.getConfigurationSection(p);
    }

    @Override
    public String getString(String path, String def) {
        if (cfg == null) return def;
        String p = norm(path);
        return cfg.getString(p, def);
    }

    @Override
    public boolean getBoolean(String path, boolean def) {
        if (cfg == null) return def;
        String p = norm(path);
        return cfg.contains(p) ? cfg.getBoolean(p) : def;
    }

    @Override
    public int getInt(String path, int def) {
        if (cfg == null) return def;
        String p = norm(path);
        return cfg.getInt(p, def);
    }

    @Override
    public long getLong(String path, long def) {
        if (cfg == null) return def;
        String p = norm(path);
        return cfg.getLong(p, def);
    }

    @Override
    public double getDouble(String path, double def) {
        if (cfg == null) return def;
        String p = norm(path);
        return cfg.getDouble(p, def);
    }

    @Override
    public List<String> getStringList(String path) {
        if (cfg == null) return Collections.emptyList();
        String p = norm(path);
        List<String> list = cfg.getStringList(p);
        return (list == null) ? Collections.emptyList() : list;
    }

    @Override
    public Set<String> getKeys(boolean deep) {
        if (cfg == null) return Collections.emptySet();
        return cfg.getKeys(deep);
    }

    @Override
    public Set<String> childKeys(String path) {
        if (cfg == null) return Collections.emptySet();
        ConfigurationSection sec = sectionOrRoot(path);
        return (sec == null) ? Collections.emptySet() : sec.getKeys(false);
    }

    @Override
    public boolean contains(String path) {
        if (cfg == null) return false;
        String p = norm(path);
        return cfg.contains(p);
    }

    @Override
    public Map<String, Object> getSection(String path) {
        if (cfg == null) return Collections.emptyMap();
        ConfigurationSection sec = sectionOrRoot(path);
        return (sec == null) ? Collections.emptyMap() : sec.getMapValues(true);
    }

    @Override
    public List<Object> getList(String path) {
        if (cfg == null) return List.of();
        String p = norm(path);
        List<?> list = cfg.getList(p);
        return (list == null) ? List.of() : new ArrayList<>(list);
    }
}
