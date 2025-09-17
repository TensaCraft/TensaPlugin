package ua.co.tensa.config.model;

import org.simpleyaml.configuration.ConfigurationSection;
import org.simpleyaml.configuration.file.YamlConfiguration;
import org.simpleyaml.configuration.file.YamlFile;
import ua.co.tensa.Message;
import ua.co.tensa.Tensa;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Standalone base for YAML-backed files: ensures existence, loads/saves,
 * provides a cached YamlConfiguration view and convenience getters.
 * Does not depend on legacy config classes.
 */
public abstract class YamlBackedFile {
    protected YamlFile yamlFile;
    protected final String FILE_PATH;
    private boolean firstLoad = true;

    protected YamlBackedFile(String relativePath) {
        this.FILE_PATH = Tensa.pluginPath + File.separator + relativePath;
        this.yamlFile = new YamlFile(FILE_PATH);
        reload();
    }

    protected YamlBackedFile(String relativePath, boolean absolute) {
        this.FILE_PATH = absolute ? relativePath : Tensa.pluginPath + File.separator + relativePath;
        this.yamlFile = new YamlFile(FILE_PATH);
        reload();
    }

    public synchronized void reload() {
        try {
            if (!yamlFile.exists()) {
                yamlFile.createNewFile(true);
                yamlFile.load();
                populateConfigFile();
                yamlFile.save();
            } else {
                yamlFile.load();
                populateConfigFile();
                yamlFile.save();
            }
        } catch (Exception e) {
            try {
                File f = new File(FILE_PATH);
                if (f.exists()) {
                    File bak = new File(FILE_PATH + ".corrupt." + System.currentTimeMillis());
                    //noinspection ResultOfMethodCallIgnored
                    f.renameTo(bak);
                    Message.warn("Config parse error for " + FILE_PATH + ": " + e.getMessage() + ". Backed up to " + bak.getName());
                }
                yamlFile = new YamlFile(FILE_PATH);
                yamlFile.createNewFile(true);
                yamlFile.load();
                populateConfigFile();
                yamlFile.save();
            } catch (Exception ex) {
                Message.error("Failed to recover config " + FILE_PATH + ": " + ex.getMessage());
            }
        } finally {
            firstLoad = false;
        }
    }

    protected abstract void populateConfigFile();

    protected void setConfigValue(String path, Object defaultValue) {
        if (!yamlFile.contains(path)) yamlFile.set(path, defaultValue);
    }

    public YamlConfiguration getReloadedFile() { reload(); return yamlFile; }
    public YamlConfiguration getConfig() { return yamlFile; }

    public void save() { try { yamlFile.save(); } catch (IOException e) { Message.error(e.getMessage()); } }

    public void setHeader(String header) { yamlFile.setHeader(header); }
    public void setComment(String path, String comment) { yamlFile.setComment(path, comment); }
    public void setBlankLine(String path) { yamlFile.setBlankLine(path); }

    protected boolean isFirstLoad() { return firstLoad; }

    // Convenience getters
    public String getString(String path, String def) { return yamlFile.getString(path, def); }
    public boolean getBoolean(String path, boolean def) { return yamlFile.contains(path) ? yamlFile.getBoolean(path) : def; }
    public int getInt(String path, int def) { return yamlFile.getInt(path, def); }
    public long getLong(String path, long def) { return yamlFile.getLong(path, def); }
    public double getDouble(String path, double def) { return yamlFile.getDouble(path, def); }
    public List<String> getStringList(String path) { List<String> l = yamlFile.getStringList(path); return l == null ? List.of() : l; }
    public Set<String> getKeys(boolean deep) { return yamlFile.getKeys(deep); }
    public boolean contains(String path) { return yamlFile.contains(path); }
    public Map<String, Object> getSection(String path) {
        if (path == null || path.isBlank()) return yamlFile.getMapValues(true);
        ConfigurationSection sec = yamlFile.getConfigurationSection(path);
        return (sec == null) ? Collections.emptyMap() : sec.getMapValues(true);
    }
    public Set<String> childKeys(String path) {
        if (path == null || path.isBlank()) return yamlFile.getKeys(false);
        ConfigurationSection sec = yamlFile.getConfigurationSection(path);
        return (sec == null) ? Collections.emptySet() : sec.getKeys(false);
    }
    public List<Object> getList(String path) { List<?> l = yamlFile.getList(path); return l == null ? List.of() : new ArrayList<>(l); }

    public String getFilePath() { return FILE_PATH; }
    public boolean fileExists() { return yamlFile != null && yamlFile.exists(); }

    // Minimal adapter for existing code paths
    public YamlAdapter adapter() {
        YamlConfiguration cfg = getConfig();
        return new YamlAdapter() {
            @Override public String getString(String path, String def) { return cfg.getString(path, def); }
            @Override public boolean getBoolean(String path, boolean def) { return cfg.contains(path) ? cfg.getBoolean(path) : def; }
            @Override public int getInt(String path, int def) { return cfg.getInt(path, def); }
            @Override public long getLong(String path, long def) { return cfg.getLong(path, def); }
            @Override public double getDouble(String path, double def) { return cfg.getDouble(path, def); }
            @Override public List<String> getStringList(String path) { List<String> l = cfg.getStringList(path); return l == null ? List.of() : l; }
            @Override public Set<String> getKeys(boolean deep) { return cfg.getKeys(deep); }
            @Override public Set<String> childKeys(String path) { ConfigurationSection s = (path == null||path.isBlank())? cfg: cfg.getConfigurationSection(path); return s==null? Set.of(): s.getKeys(false); }
            @Override public boolean contains(String path) { return cfg.contains(path); }
            @Override public Map<String, Object> getSection(String path) { ConfigurationSection s = (path == null||path.isBlank())? cfg: cfg.getConfigurationSection(path); return s==null? Map.of() : s.getMapValues(true); }
            @Override public List<Object> getList(String path) { List<?> l = cfg.getList(path); return l==null? List.of(): new ArrayList<>(l); }
        };
    }
}
