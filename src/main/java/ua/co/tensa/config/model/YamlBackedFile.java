package ua.co.tensa.config.model;

import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;
import ua.co.tensa.Message;
import ua.co.tensa.Tensa;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Configurate-backed YAML file facade used by plugin configs.
 */
public abstract class YamlBackedFile {
    protected CommentedConfigurationNode yamlFile;
    protected final String FILE_PATH;

    private final Path filePath;
    private YamlConfigurationLoader loader;
    private boolean firstLoad = true;
    private boolean dirty = false;

    protected YamlBackedFile(String relativePath) {
        this(relativePath, false);
    }

    protected YamlBackedFile(String relativePath, boolean absolute) {
        this.filePath = absolute
                ? Path.of(relativePath).toAbsolutePath().normalize()
                : Tensa.pluginPath.resolve(relativePath).toAbsolutePath().normalize();
        this.FILE_PATH = filePath.toString();
        this.loader = YamlFileIO.loader(filePath);
        reload();
    }

    public synchronized void reload() {
        boolean created = false;

        try {
            dirty = false;
            created = ensureFileExists();
            yamlFile = YamlFileIO.load(loader);
            populateConfigFile();

            if (created || dirty) {
                saveAutoUpdate(created);
            }
        } catch (Exception e) {
            recover(e);
        } finally {
            firstLoad = false;
            dirty = false;
        }
    }

    private boolean ensureFileExists() throws IOException {
        if (Files.exists(filePath)) {
            return false;
        }

        Path parent = filePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.createFile(filePath);
        return true;
    }

    private void saveAutoUpdate(boolean created) {
        try {
            YamlFileIO.saveValidated(loader, yamlFile, filePath);
            dirty = false;
        } catch (IOException e) {
            String impact = created ? "Generated file was not completed." : "Existing file was not replaced.";
            Message.error("Failed to save config " + FILE_PATH + ": " + e.getMessage() + ". " + impact);
        }
    }

    private void recover(Exception cause) {
        try {
            if (Files.exists(filePath)) {
                Path backup = filePath.resolveSibling(filePath.getFileName() + ".corrupt." + System.currentTimeMillis());
                Files.move(filePath, backup, StandardCopyOption.REPLACE_EXISTING);
                Message.warn("Config parse error for " + FILE_PATH + ": " + cause.getMessage() + ". Backed up to " + backup.getFileName());
            }

            ensureFileExists();
            loader = YamlFileIO.loader(filePath);
            yamlFile = YamlFileIO.load(loader);
            dirty = false;
            populateConfigFile();
            if (dirty || Files.exists(filePath)) {
                YamlFileIO.saveValidated(loader, yamlFile, filePath);
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
        CommentedConfigurationNode node = node(path);
        if (node.virtual() || node.raw() == null) {
            setNodeValue(node, defaultValue);
            markDirty();
        }
    }

    public CommentedConfigurationNode getReloadedFile() {
        reload();
        return yamlFile;
    }

    public CommentedConfigurationNode getConfig() {
        return yamlFile;
    }

    public void save() {
        try {
            YamlFileIO.saveValidated(loader, yamlFile, filePath);
            dirty = false;
        } catch (IOException e) {
            Message.error(e.getMessage());
        }
    }

    public void setHeader(String header) {
        yamlFile.comment(header);
        markDirty();
    }

    public void setComment(String path, String comment) {
        node(path).comment(comment);
        markDirty();
    }

    public void setBlankLine(String path) {
        // Configurate does not expose explicit blank-line comments for YAML.
    }

    protected boolean isFirstLoad() {
        return firstLoad;
    }

    public String getString(String path, String def) {
        return node(path).getString(def);
    }

    public boolean getBoolean(String path, boolean def) {
        CommentedConfigurationNode node = node(path);
        return node.virtual() ? def : node.getBoolean(def);
    }

    public int getInt(String path, int def) {
        return node(path).getInt(def);
    }

    public long getLong(String path, long def) {
        return node(path).getLong(def);
    }

    public double getDouble(String path, double def) {
        return node(path).getDouble(def);
    }

    public List<String> getStringList(String path) {
        try {
            return new ArrayList<>(node(path).getList(String.class, List.of()));
        } catch (SerializationException e) {
            return List.of();
        }
    }

    public Set<String> getKeys(boolean deep) {
        Set<String> keys = new LinkedHashSet<>();
        collectKeys(yamlFile, "", deep, keys);
        return keys;
    }

    public boolean contains(String path) {
        CommentedConfigurationNode node = node(path);
        return !node.virtual() && node.raw() != null;
    }

    public Map<String, Object> getSection(String path) {
        return toMap(path == null || path.isBlank() ? yamlFile : node(path));
    }

    public Set<String> childKeys(String path) {
        CommentedConfigurationNode section = path == null || path.isBlank() ? yamlFile : node(path);
        Set<String> keys = new LinkedHashSet<>();
        for (Object key : section.childrenMap().keySet()) {
            keys.add(String.valueOf(key));
        }
        return keys;
    }

    public List<Object> getList(String path) {
        List<Object> out = new ArrayList<>();
        for (CommentedConfigurationNode child : node(path).childrenList()) {
            out.add(toJavaValue(child));
        }
        return out;
    }

    public String getFilePath() {
        return FILE_PATH;
    }

    public boolean fileExists() {
        return Files.exists(filePath);
    }

    protected CommentedConfigurationNode node(String path) {
        if (path == null || path.isBlank()) {
            return yamlFile;
        }
        return yamlFile.node((Object[]) path.split("\\."));
    }

    protected void setNodeValue(CommentedConfigurationNode node, Object value) {
        try {
            node.set(copyValue(value));
        } catch (SerializationException e) {
            Message.warn("Failed to write config value in " + FILE_PATH + ": " + e.getMessage());
        }
    }

    public YamlAdapter adapter() {
        return new YamlAdapter() {
            @Override
            public String getString(String path, String def) {
                return YamlBackedFile.this.getString(path, def);
            }

            @Override
            public boolean getBoolean(String path, boolean def) {
                return YamlBackedFile.this.getBoolean(path, def);
            }

            @Override
            public int getInt(String path, int def) {
                return YamlBackedFile.this.getInt(path, def);
            }

            @Override
            public long getLong(String path, long def) {
                return YamlBackedFile.this.getLong(path, def);
            }

            @Override
            public double getDouble(String path, double def) {
                return YamlBackedFile.this.getDouble(path, def);
            }

            @Override
            public List<String> getStringList(String path) {
                return YamlBackedFile.this.getStringList(path);
            }

            @Override
            public Set<String> getKeys(boolean deep) {
                return YamlBackedFile.this.getKeys(deep);
            }

            @Override
            public Set<String> childKeys(String path) {
                return YamlBackedFile.this.childKeys(path);
            }

            @Override
            public boolean contains(String path) {
                return YamlBackedFile.this.contains(path);
            }

            @Override
            public Map<String, Object> getSection(String path) {
                return YamlBackedFile.this.getSection(path);
            }

            @Override
            public List<Object> getList(String path) {
                return YamlBackedFile.this.getList(path);
            }
        };
    }

    private void collectKeys(CommentedConfigurationNode node, String prefix, boolean deep, Set<String> keys) {
        for (Map.Entry<Object, ? extends CommentedConfigurationNode> entry : node.childrenMap().entrySet()) {
            String key = prefix.isBlank() ? String.valueOf(entry.getKey()) : prefix + "." + entry.getKey();
            keys.add(key);
            if (deep) {
                collectKeys(entry.getValue(), key, true, keys);
            }
        }
    }

    private Map<String, Object> toMap(CommentedConfigurationNode node) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<Object, ? extends CommentedConfigurationNode> entry : node.childrenMap().entrySet()) {
            map.put(String.valueOf(entry.getKey()), toJavaValue(entry.getValue()));
        }
        return map;
    }

    private Object toJavaValue(CommentedConfigurationNode node) {
        if (!node.childrenMap().isEmpty()) {
            return toMap(node);
        }
        if (!node.childrenList().isEmpty()) {
            List<Object> list = new ArrayList<>();
            for (CommentedConfigurationNode child : node.childrenList()) {
                list.add(toJavaValue(child));
            }
            return list;
        }
        return copyValue(node.raw());
    }

    protected Object copyValue(Object value) {
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
