package ua.co.tensa.modules.runtime;

import ua.co.tensa.modules.AbstractModule;
import ua.co.tensa.modules.ModuleEntry;

import java.time.Duration;

/** Visible system module defining the fixed bounded scheduling policy. */
public final class SchedulerModule extends AbstractModule {
    private static final SchedulerModule INSTANCE = new SchedulerModule();
    public static final ModuleEntry ENTRY = INSTANCE;

    private SchedulerModule() {
        super("scheduler", "Task Scheduler");
    }

    public static ModuleScheduler create(String owner) {
        if (!INSTANCE.isEnabled()) {
            throw new IllegalStateException("Task Scheduler system module is unavailable");
        }
        return new ModuleScheduler(owner, defaults());
    }

    public static ModuleScheduler.Defaults defaults() {
        return new ModuleScheduler.Defaults(
                512,
                2,
                256,
                Duration.ofSeconds(30),
                1,
                Duration.ofSeconds(1),
                Duration.ofSeconds(30),
                0.2
        );
    }

    @Override
    public boolean required() {
        return true;
    }

    @Override
    protected void onEnable() {
        // Runtime schedulers are created per owning module to avoid shared mutable job state.
    }

    @Override
    protected void onDisable() {
    }

    @Override
    protected void onReload() {
        // Policy is compiled and immutable; active owner runtimes remain untouched.
    }

    @Override
    protected boolean restartOnReloadFailure() {
        return false;
    }
}
