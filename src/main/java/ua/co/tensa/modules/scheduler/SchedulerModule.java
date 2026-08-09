package ua.co.tensa.modules.scheduler;

import ua.co.tensa.Tensa;
import ua.co.tensa.Util;
import ua.co.tensa.config.model.YamlConfigPreflight;
import ua.co.tensa.modules.AbstractModule;
import ua.co.tensa.modules.ModuleEntry;
import ua.co.tensa.modules.runtime.AtomicRuntimeSlot;
import ua.co.tensa.text.TextPipeline;

/** Configurable periodic console command scheduler. */
public final class SchedulerModule {
    private static final SchedulerModuleEntry IMPL = new SchedulerModuleEntry();
    public static final ModuleEntry ENTRY = IMPL;

    private SchedulerModule() {
    }

    private static ScheduledCommandsPlan prepare() {
        YamlConfigPreflight.validate(Tensa.pluginPath.resolve("scheduler/config.yml"));
        ScheduledCommandsConfig config = new ScheduledCommandsConfig();
        config.reloadCfg();
        return ScheduledCommandsPlan.from(config.adapter());
    }

    private static final class SchedulerModuleEntry extends AbstractModule {
        private final AtomicRuntimeSlot<ScheduledCommandsPlan, ScheduledCommandsRuntime> runtime =
                new AtomicRuntimeSlot<>(
                        plan -> new ScheduledCommandsRuntime(
                                plan,
                                command -> Util.executeCommand(TextPipeline.resolvePlaceholders(null, command))
                        ),
                        ScheduledCommandsRuntime::close
                );

        private SchedulerModuleEntry() {
            super("scheduler", "Command Scheduler");
        }

        @Override
        protected void onEnable() {
            runtime.start(prepare());
        }

        @Override
        protected void onDisable() {
            runtime.close();
        }

        @Override
        protected void onReload() {
            runtime.replace(prepare());
        }

        @Override
        protected boolean restartOnReloadFailure() {
            return false;
        }
    }
}
