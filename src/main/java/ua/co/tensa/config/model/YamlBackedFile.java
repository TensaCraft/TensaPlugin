package ua.co.tensa.config.model;

import org.simpleyaml.configuration.ConfigurationSection;
import org.simpleyaml.configuration.file.YamlConfiguration;
import org.simpleyaml.configuration.file.YamlFile;
import ua.co.tensa.Message;
import ua.co.tensa.Tensa;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Standalone base for YAML-backed files: ensures existence, loads/saves,
 * provides a cached YamlConfiguration view and convenience getters.
 * Does not depend on legacy config classes.
 */
public abstract class YamlBackedFile {
    protected YamlFile yamlFile;
    protected final String FILE_PATH;

    private boolean firstLoad = true;
    private boolean dirty = false;

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
        boolean created = false;

        try {
            dirty = false;

            try {
                if (!yamlFile.exists()) {
                    yamlFile.createNewFile(true);
                    created = true;
                }
            } catch (IOException e) {
                Message.error("Failed to prepare config " + FILE_PATH + ": " + e.getMessage());
                return;
            }

            try {
                YamlFileIO.loadWithComments(yamlFile);
            } catch (Exception e) {
                recover(e);
                return;
            }

            try {
                populateConfigFile();
            } catch (Exception e) {
                Message.error("Failed to update config " + FILE_PATH + ": " + e.getMessage());
                return;
            }

            if (created || dirty) {
                saveAutoUpdate(created);
            }
        } finally {
            firstLoad = false;
            dirty = false;
        }
    }

    private void saveAutoUpdate(boolean created) {
        try {
            YamlFileIO.saveValidated(yamlFile);
            dirty = false;
        } catch (IOException e) {
            String impact = created ? "Generated file was not completed." : "Existing file was not replaced.";
            Message.error("Failed to save config " + FILE_PATH + ": " + e.getMessage() + ". " + impact);
        }
    }

    private void recover(Exception cause) {
        try {
            File file = new File(FILE_PATH);
            if (file.exists()) {
                File backup = new File(FILE_PATH + ".corrupt." + System.currentTimeMillis());
                //noinspection ResultOfMethodCallIgnored
                file.renameTo(backup);
                Message.warn("Config parse error for " + FILE_PATH + ": " + cause.getMessage() + ". Backed up to " + backup.getName());
            }

            yamlFile = new YamlFile(FILE_PATH);
            yamlFile.createNewFile(true);
            YamlFileIO.loadWithComments(yamlFile);

            dirty = false;
            populateConfigFile();

            if (dirty || yamlFile.exists()) {
                YamlFileIO.saveValidated(yamlFile);
                dirty = false;
            }
        } catch (Exception ex) {
            Message.error("Failed to recover config " + FILE_PATH + ": " + ex.getMessage());
        }
    }

    protected abstract void populateConfigFile();

    protected void markDirty() {
        this.dirty = true;
    }

    protected boolean isDirty() {
        return dirty;
    }

    protected void setConfigValue(String path, Object defaultValue) {
        if (!yamlFile.contains(path)) {
            yamlFile.set(path, defaultValue);
            markDirty();
        }
    }

    public YamlConfiguration getReloadedFile() {
        reload();
        return yamlFile;
    }

    public YamlConfiguration getConfig() {
        return yamlFile;
    }

    public void save() {
        try {
            YamlFileIO.saveValidated(yamlFile);
            dirty = false;
        } catch (IOException e) {
            Message.error(e.getMessage());
        }
    }

    public void setHeader(String header) {
        yamlFile.setHeader(header);
        markDirty();
    }

    public void setComment(String path, String comment) {
        yamlFile.setComment(path, comment);
        markDirty();
    }

    public void setBlankLine(String path) {
        yamlFile.setBlankLine(path);
        markDirty();
    }

    protected boolean isFirstLoad() {
        return firstLoad;
    }

    public String getString(String path, String def) {
        return yamlFile.getString(path, def);
    }

    public boolean getBoolean(String path, boolean def) {
        return yamlFile.contains(path) ? yamlFile.getBoolean(path) : def;
    }

    public int getInt(String path, int def) {
        return yamlFile.getInt(path, def);
    }

    public long getLong(String path, long def) {
        return yamlFile.getLong(path, def);
    }

    public double getDouble(String path, double def) {
        return yamlFile.getDouble(path, def);
    }

    public List<String> getStringList(String path) {
        List<String> list = yamlFile.getStringList(path);
        return list == null ? List.of() : list;
    }

    public Set<String> getKeys(boolean deep) {
        return yamlFile.getKeys(deep);
    }

    public boolean contains(String path) {
        return yamlFile.contains(path);
    }

    public Map<String, Object> getSection(String path) {
        if (path == null || path.isBlank()) {
            return yamlFile.getMapValues(true);
        }

        ConfigurationSection sec = yamlFile.getConfigurationSection(path);
        return sec == null ? Collections.emptyMap() : sec.getMapValues(true);
    }

    public Set<String> childKeys(String path) {
        if (path == null || path.isBlank()) {
            return yamlFile.getKeys(false);
        }

        ConfigurationSection sec = yamlFile.getConfigurationSection(path);
        return sec == null ? Collections.emptySet() : sec.getKeys(false);
    }

    public List<Object> getList(String path) {
        List<?> list = yamlFile.getList(path);
        return list == null ? List.of() : new ArrayList<>(list);
    }

    public String getFilePath() {
        return FILE_PATH;
    }

    public boolean fileExists() {
        return yamlFile != null && yamlFile.exists();
    }

    public YamlAdapter adapter() {
        YamlConfiguration cfg = getConfig();
        return new YamlAdapter() {
            @Override
            public String getString(String path, String def) {
                return cfg.getString(path, def);
            }

            @Override
            public boolean getBoolean(String path, boolean def) {
                return cfg.contains(path) ? cfg.getBoolean(path) : def;
            }

            @Override
            public int getInt(String path, int def) {
                return cfg.getInt(path, def);
            }

            @Override
            public long getLong(String path, long def) {
                return cfg.getLong(path, def);
            }

            @Override
            public double getDouble(String path, double def) {
                return cfg.getDouble(path, def);
            }

            @Override
            public List<String> getStringList(String path) {
                List<String> list = cfg.getStringList(path);
                return list == null ? List.of() : list;
            }

            @Override
            public Set<String> getKeys(boolean deep) {
                return cfg.getKeys(deep);
            }

            @Override
            public Set<String> childKeys(String path) {
                ConfigurationSection section = (path == null || path.isBlank()) ? cfg : cfg.getConfigurationSection(path);
                return section == null ? Set.of() : section.getKeys(false);
            }

            @Override
            public boolean contains(String path) {
                return cfg.contains(path);
            }

            @Override
            public Map<String, Object> getSection(String path) {
                ConfigurationSection section = (path == null || path.isBlank()) ? cfg : cfg.getConfigurationSection(path);
                return section == null ? Map.of() : section.getMapValues(true);
            }

            @Override
            public List<Object> getList(String path) {
                List<?> list = cfg.getList(path);
                return list == null ? List.of() : new ArrayList<>(list);
            }
        };
    }
}
