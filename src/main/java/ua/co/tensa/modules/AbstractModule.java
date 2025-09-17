package ua.co.tensa.modules;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.scheduler.ScheduledTask;
import ua.co.tensa.Util;
import ua.co.tensa.config.model.YamlAdapter;
import ua.co.tensa.config.model.YamlBackedFile;

/**
 * Base class for modules providing a consistent lifecycle
 * and handy helpers for common tasks (command registration,
 * logging, and config initialization).
 */
public abstract class AbstractModule implements ModuleEntry {
    private final String id;
    private final String title;
    private boolean enabled;
    private final java.util.List<Object> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<ScheduledTask> tasks = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<String> placeholders = new java.util.concurrent.CopyOnWriteArrayList<>();

    protected AbstractModule(String id, String title) {
        this.id = id;
        this.title = title;
    }

    // Lifecycle wiring
    @Override
    public final String id() { return id; }

    @Override
    public final String title() { return title; }

    protected final void doEnable(boolean logStatus) {
        if (enabled) return;
        try {
            onEnable();
            enabled = true;
            if (logStatus) ModuleStatusLogger.enabled(id, title);
        } catch (Throwable t) {
            ua.co.tensa.Message.error("Enable failed for module '" + id + "': " + t.getMessage());
            enabled = false;
        }
    }

    @Override
    public final void enable() { doEnable(true); }

    protected final void doDisable(boolean logStatus) {
        if (!enabled) return;
        try {
            onDisable();
        } catch (Throwable t) {
            ua.co.tensa.Message.warn("Disable failed for module '" + id + "': " + t.getMessage());
        } finally {
            unregisterAllListeners();
            cancelAllTasks();
            unregisterAllPlaceholders();
            enabled = false;
            if (logStatus) ModuleStatusLogger.disabled(id, title);
        }
    }

    @Override
    public final void disable() { doDisable(true); }

    @Override
    public boolean isEnabled() { return enabled; }

    // Hooks to implement in modules
    protected abstract void onEnable();
    protected abstract void onDisable();
    protected void onReload() { /* optional soft reload */ }

    // Reload: if onReload is overridden -> use it; otherwise perform full restart
    @Override
    public void reload() {
        if (!isEnabled()) { enable(); return; }
        boolean overridden = false;
        try {
            // Detect if subclass provided its own onReload implementation
            overridden = this.getClass().getDeclaredMethod("onReload").getDeclaringClass() != AbstractModule.class;
        } catch (NoSuchMethodException ignored) {
            overridden = false;
        }
        if (overridden) {
            try {
                onReload();
                ModuleStatusLogger.reloaded(id, title);
                return;
            } catch (Throwable t) {
                ua.co.tensa.Message.warn("Soft reload failed for '" + id + "': " + t.getMessage() + "; restarting module");
            }
        }
        try { doDisable(false); } catch (Throwable ignored) {}
        doEnable(false);
        ModuleStatusLogger.reloaded(id, title);
    }

    // Helpers (static where instance state isn't required)
    public static void registerCommand(String command, String alias, SimpleCommand handler) {
        Util.registerCommand(command, alias, handler);
    }

    public static void unregisterCommands(String... commands) {
        if (commands == null) return;
        for (String c : commands) {
            if (c != null && !c.isBlank()) Util.unregisterCommand(c);
        }
    }

    // Logging: use Message directly in call sites for consistency with localization

    /**
     * Ensure a config file exists and is loaded, then return a view.
     */
    public static void ensureConfig(YamlBackedFile cfg) {
        cfg.getReloadedFile();
    }

    /** Ensure config exists and get its adapter view. */
    public static YamlAdapter ensureAdapter(YamlBackedFile cfg) {
        ensureConfig(cfg);
        return cfg.adapter();
    }

    // Event listener helpers
    /** Register and track a listener so it can be auto-unregistered on disable. */
    public <T> T registerListener(T listener) {
        if (listener == null) return null;
        EventManager em = ua.co.tensa.Tensa.server.getEventManager();
        em.register(ua.co.tensa.Tensa.pluginContainer, listener);
        listeners.add(listener);
        return listener;
    }

    /** Unregister a previously registered listener and stop tracking it. */
    public void unregisterListener(Object listener) {
        if (listener == null) return;
        try { ua.co.tensa.Tensa.server.getEventManager().unregisterListener(ua.co.tensa.Tensa.pluginContainer, listener); } catch (Throwable ignored) {}
        listeners.remove(listener);
    }

    /** Unregister all listeners that were registered via this module's helper. */
    public void unregisterAllListeners() {
        EventManager em = ua.co.tensa.Tensa.server.getEventManager();
        for (Object l : listeners) {
            try { em.unregisterListener(ua.co.tensa.Tensa.pluginContainer, l); } catch (Throwable ignored) {}
        }
        listeners.clear();
    }

    // Placeholder helpers
    protected void registerPlaceholder(String key, java.util.function.Function<com.velocitypowered.api.proxy.Player, String> resolver) {
        if (key == null || resolver == null) return;
        ua.co.tensa.placeholders.PlaceholderManager.register(key, resolver);
        placeholders.add(key);
    }
    protected void unregisterAllPlaceholders() {
        for (String k : placeholders) {
            try { ua.co.tensa.placeholders.PlaceholderManager.unregister(k); } catch (Throwable ignored) {}
        }
        placeholders.clear();
    }

    // Scheduler helpers: auto-cancel on disable
    public ScheduledTask schedule(java.lang.Runnable task, long delay, java.util.concurrent.TimeUnit unit) {
        if (task == null) return null;
        ScheduledTask t = ua.co.tensa.Tensa.server.getScheduler()
                .buildTask(ua.co.tensa.Tensa.pluginContainer, task)
                .delay(delay, unit)
                .schedule();
        tasks.add(t);
        return t;
    }

    public ScheduledTask scheduleRepeating(java.lang.Runnable task, long delay, long repeat, java.util.concurrent.TimeUnit unit) {
        if (task == null) return null;
        ScheduledTask t = ua.co.tensa.Tensa.server.getScheduler()
                .buildTask(ua.co.tensa.Tensa.pluginContainer, task)
                .delay(delay, unit)
                .repeat(repeat, unit)
                .schedule();
        tasks.add(t);
        return t;
    }

    protected void cancelAllTasks() {
        for (ScheduledTask t : tasks) {
            try { t.cancel(); } catch (Throwable ignored) {}
        }
        tasks.clear();
    }
}
