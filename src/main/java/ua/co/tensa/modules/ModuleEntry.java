package ua.co.tensa.modules;

/**
 * Lightweight lifecycle interface for modules to standardize
 * enabling, disabling and reloading logic.
 */
public interface ModuleEntry {
    String id();
    String title();
    void enable();
    void disable();
    default void reload() {
        // Default reload behavior: disable → enable
        disable();
        enable();
    }
    boolean isEnabled();

    static ModuleEntry of(String id, String title, Runnable onEnable, Runnable onDisable) {
        return new ModuleEntry() {
            private boolean enabled;
            @Override public String id() { return id; }
            @Override public String title() { return title; }
            @Override public void enable() { onEnable.run(); enabled = true; }
            @Override public void disable() { onDisable.run(); enabled = false; }
            @Override public boolean isEnabled() { return enabled; }
        };
    }
}
