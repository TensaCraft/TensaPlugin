package ua.co.tensa.config.core;

import org.simpleyaml.configuration.file.YamlConfiguration;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class YamlConfigAdapter implements ConfigAdapter {
    private final YamlConfiguration cfg;

    public YamlConfigAdapter(YamlConfiguration cfg) {
        this.cfg = cfg;
    }

    @Override
    public String getString(String path, String def) {
        return cfg == null ? def : cfg.getString(path, def);
    }

    @Override
    public boolean getBoolean(String path, boolean def) {
        return cfg != null && cfg.contains(path) ? cfg.getBoolean(path) : def;
    }

    @Override
    public int getInt(String path, int def) {
        return cfg == null ? def : cfg.getInt(path, def);
    }

    @Override
    public long getLong(String path, long def) {
        return cfg == null ? def : cfg.getLong(path, def);
    }

    @Override
    public double getDouble(String path, double def) {
        return cfg == null ? def : cfg.getDouble(path, def);
    }

    @Override
    public List<String> getStringList(String path) {
        if (cfg == null) return Collections.emptyList();
        List<String> list = cfg.getStringList(path);
        return list == null ? Collections.emptyList() : list;
    }

    @Override
    public Set<String> getKeys(boolean deep) {
        return cfg == null ? Collections.emptySet() : cfg.getKeys(deep);
    }

    @Override
    public Set<String> childKeys(String path) {
        if (cfg == null) return Collections.emptySet();
        org.simpleyaml.configuration.ConfigurationSection sec = cfg.getConfigurationSection(path);
        return sec == null ? Collections.emptySet() : sec.getKeys(false);
    }

    @Override
    public boolean contains(String path) {
        return cfg != null && cfg.contains(path);
    }

    @Override
    public Map<String, Object> getSection(String path) {
        if (cfg == null || cfg.getConfigurationSection(path) == null) return Collections.emptyMap();
        return cfg.getConfigurationSection(path).getMapValues(true);
    }

    @Override
    public java.util.List<Object> getList(String path) {
        if (cfg == null) return java.util.List.of();
        java.util.List<?> list = cfg.getList(path);
        return list == null ? java.util.List.of() : new java.util.ArrayList<>(list);
    }
}
