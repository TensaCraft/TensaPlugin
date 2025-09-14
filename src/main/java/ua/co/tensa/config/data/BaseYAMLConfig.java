package ua.co.tensa.config.data;

import org.simpleyaml.configuration.file.YamlConfiguration;
import org.simpleyaml.configuration.file.YamlFile;
import ua.co.tensa.Message;
import ua.co.tensa.Tensa;

import java.io.File;
import java.io.IOException;

/**
 * Base YAML config holder.
 * Ensures the file is loaded on construction and keeps a cached YamlConfiguration view.
 */
public abstract class BaseYAMLConfig {
    protected YamlFile yamlFile;
    private YamlConfiguration view; // cached, reflects last successful load/save
    protected final String FILE_PATH;

    protected BaseYAMLConfig(String relativePath) {
        this.FILE_PATH = Tensa.pluginPath + File.separator + relativePath;
        this.yamlFile = new YamlFile(FILE_PATH);

        // Load immediately so adapter/getters see actual data
        reload();

        ua.co.tensa.config.ConfigRegistry.register(this);
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
                populateConfigFile(); // write any new defaults
                yamlFile.save();
            }
        } catch (Exception e) {
            // Backup corrupt file and recreate
            try {
                File f = new File(FILE_PATH);
                if (f.exists()) {
                    File bak = new File(FILE_PATH + ".corrupt." + System.currentTimeMillis());
                    // noinspection ResultOfMethodCallIgnored
                    f.renameTo(bak);
                    Message.warn("Config parse error for " + FILE_PATH + ": " + e.getMessage()
                            + ". Backed up to " + bak.getName());
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
            try {
                this.view = new YamlConfiguration(yamlFile);
            } catch (Throwable t) {
                this.view = new YamlConfiguration(); // never null
            }
        }
    }

    protected abstract void populateConfigFile();

    protected void setConfigValue(String path, Object defaultValue) {
        if (!yamlFile.contains(path)) {
            yamlFile.set(path, defaultValue);
        }
    }

    /** Fresh view after reload. */
    public YamlConfiguration getReloadedFile() {
        reload();
        return view;
    }

    /** Cached view (last loaded). */
    public YamlConfiguration getConfig() {
        return view;
    }

    /** Stable adapter over the cached view. */
    public ua.co.tensa.config.core.ConfigAdapter adapter() {
        return new ua.co.tensa.config.core.YamlConfigAdapter(getConfig());
    }

    // Convenience getters
    public String getString(String path, String def) {
        return getConfig().getString(path, def);
    }

    public boolean getBoolean(String path, boolean def) {
        return getConfig().contains(path) ? getConfig().getBoolean(path) : def;
    }

    public int getInt(String path, int def) {
        return getConfig().getInt(path, def);
    }

    public long getLong(String path, long def) {
        return getConfig().getLong(path, def);
    }

    public double getDouble(String path, double def) {
        return getConfig().getDouble(path, def);
    }

    public java.util.List<String> getStringList(String path) {
        java.util.List<String> list = getConfig().getStringList(path);
        return list == null ? java.util.List.of() : list;
    }

    public java.util.Set<String> getKeys(boolean deep) {
        return getConfig().getKeys(deep);
    }

    public boolean contains(String path) {
        return getConfig().contains(path);
    }

    public void save() {
        try {
            yamlFile.save();
        } catch (IOException e) {
            Message.error(e.getMessage());
        }
    }

    public void syncMissingKeysFrom(YamlConfiguration template) {
        if (template == null) return;
        try {
            boolean changed = false;
            for (String key : template.getKeys(true)) {
                if (template.isConfigurationSection(key)) continue;
                if (!yamlFile.contains(key)) {
                    yamlFile.set(key, template.get(key));
                    changed = true;
                }
            }
            if (changed) yamlFile.save();
        } catch (Exception e) {
            Message.warn("Failed to sync config keys for " + FILE_PATH + ": " + e.getMessage());
        }
    }
}
