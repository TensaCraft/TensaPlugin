package ua.co.tensa.config.model;

import org.simpleyaml.configuration.file.YamlConfiguration;
import ua.co.tensa.Message;

/**
 * Base for annotation-driven config models built on YamlBackedFile.
 */
public abstract class ConfigBase extends YamlBackedFile {
    private ConfigBinder binder;

    private void ensureBinder() {
        if (this.binder == null) {
            this.binder = new ConfigBinder(this);
        }
    }

    protected ConfigBase(String relativePath) {
        super(relativePath);
    }

    @Override
    protected final void populateConfigFile() {
        ensureBinder();
        // Avoid writing defaults on the very first load (constructor-time) before field initializers apply
        if (!isFirstLoad()) {
            try {
                binder.writeMissingDefaults(this.yamlFile);
            } catch (Exception e) {
                Message.warn("Failed to write defaults for model " + getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
        if (!isFirstLoad()) {
            try {
                YamlConfiguration cfg = getConfig();
                binder.loadFromYaml(cfg);
            } catch (Exception e) {
                Message.warn("Failed to load model values for " + getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
    }

    public synchronized void reloadCfg() {
        super.reload();
        ensureBinder();
        try { binder.loadFromYaml(getConfig()); } catch (Exception ignored) {}
    }

    /** Backwards-compatible name used by callers. */
    public synchronized void reloadModel() { reloadCfg(); }

    /** Hook for subclasses to skip writing specific defaults (module-specific policies). */
    protected boolean shouldWriteDefault(String basePath, Object defaultValue, org.simpleyaml.configuration.file.YamlFile yaml) {
        return true;
    }
}
