package ua.co.tensa.modules.runtime;

import java.time.Duration;

/** Internal lifecycle scheduling policy. It is not a configurable Tensa module. */
public final class RuntimeSchedulerFactory {
    private RuntimeSchedulerFactory() {
    }

    public static ModuleScheduler create(String owner) {
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
}
