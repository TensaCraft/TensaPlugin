package ua.co.tensa.modules.queue;

import com.velocitypowered.api.scheduler.ScheduledTask;
import ua.co.tensa.Tensa;
import ua.co.tensa.config.model.YamlConfigPreflight;
import ua.co.tensa.modules.AbstractModule;
import ua.co.tensa.modules.ModuleEntry;
import ua.co.tensa.modules.queue.data.CommandQueueConfig;
import ua.co.tensa.modules.runtime.AtomicRuntimeSlot;

import java.util.concurrent.TimeUnit;

public final class CommandQueueModule {
    private static final QueueModuleEntry IMPL = new QueueModuleEntry();
    public static final ModuleEntry ENTRY = IMPL;

    public static CommandQueueManager manager() {
        ActiveRuntime active = IMPL.runtime.runtime();
        return active == null ? null : active.manager();
    }

    private static CommandQueueSettings prepare() {
        YamlConfigPreflight.validate(Tensa.pluginPath.resolve("queue/config.yml"));
        CommandQueueConfig config = CommandQueueConfig.get();
        config.reloadCfg();
        return CommandQueueSettings.from(config);
    }

    private static ActiveRuntime activate(QueueModuleEntry owner, CommandQueueSettings settings) {
        CommandQueueManager manager = new CommandQueueManager(settings, Tensa.storage);
        CommandQueueListener listener = null;
        ScheduledTask task = null;
        try {
            listener = owner.registerListener(new CommandQueueListener(manager));
            task = owner.scheduleRepeating(manager::dispatchDue, 1L, settings.pollIntervalSeconds(), TimeUnit.SECONDS);
            AbstractModule.registerCommand("tqueue", "queue", new CommandQueueCommand(manager));
            manager.dispatchDue();
            return new ActiveRuntime(manager, listener, task);
        } catch (RuntimeException failure) {
            deactivate(owner, new ActiveRuntime(manager, listener, task));
            throw failure;
        }
    }

    private static void deactivate(QueueModuleEntry owner, ActiveRuntime active) {
        if (active == null) return;
        AbstractModule.unregisterCommands("tqueue", "queue");
        owner.cancelTask(active.task());
        owner.unregisterListener(active.listener());
        active.manager().close();
    }

    private static final class QueueModuleEntry extends AbstractModule {
        private final AtomicRuntimeSlot<CommandQueueSettings, ActiveRuntime> runtime =
                new AtomicRuntimeSlot<>(plan -> activate(this, plan), active -> deactivate(this, active));

        private QueueModuleEntry() {
            super("command-queue", "Command Queue");
        }

        @Override protected void onEnable() { runtime.start(prepare()); }
        @Override protected void onDisable() { runtime.close(); }
        @Override protected void onReload() { runtime.replace(prepare()); }
        @Override protected boolean restartOnReloadFailure() { return false; }
    }

    private record ActiveRuntime(
            CommandQueueManager manager,
            CommandQueueListener listener,
            ScheduledTask task
    ) {
    }
}
