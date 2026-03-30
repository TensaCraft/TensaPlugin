package ua.co.tensa.config.model;

import org.simpleyaml.configuration.file.YamlFile;
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

        /*
         * During the constructor-time load, subclass field initializers
         * are not ready yet, so defaults must not be written/read here.
         */
        if (isFirstLoad()) {
            return;
        }

        try {
            boolean changed = binder.writeMissingDefaults(this.yamlFile);
            if (changed) {
                markDirty();
            }
        } catch (Exception e) {
            Message.warn("Failed to write defaults for model " + getClass().getSimpleName() + ": " + e.getMessage());
        }

        try {
            binder.loadFromYaml(getConfig());
        } catch (Exception e) {
            Message.warn("Failed to load model values for " + getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    public synchronized void reloadCfg() {
        super.reload();
    }

    /** Backwards-compatible name used by callers. */
    public synchronized void reloadModel() {
        reloadCfg();
    }

    /** Hook for subclasses to skip writing specific defaults. */
    protected boolean shouldWriteDefault(String basePath, Object defaultValue, YamlFile yaml) {
        return true;
    }
}