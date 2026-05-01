package ua.co.tensa.modules.queue;

import ua.co.tensa.Tensa;
import ua.co.tensa.modules.AbstractModule;
import ua.co.tensa.modules.ModuleEntry;
import ua.co.tensa.modules.queue.data.CommandQueueConfig;

import java.util.concurrent.TimeUnit;

public final class CommandQueueModule {
    private static final ModuleEntry IMPL = new AbstractModule(
            "command-queue", "Command Queue") {
        @Override protected void onEnable() { CommandQueueModule.enableImpl(); }
        @Override protected void onDisable() { CommandQueueModule.disableImpl(); }
        @Override protected void onReload() { CommandQueueModule.reloadImpl(); }
    };
    public static final ModuleEntry ENTRY = IMPL;

    private static CommandQueueManager manager;

    public static CommandQueueManager manager() {
        return manager;
    }

    private static void enableImpl() {
        CommandQueueConfig config = CommandQueueConfig.get();
        config.reloadCfg();
        manager = new CommandQueueManager(config, Tensa.pluginPath);
        ((AbstractModule) IMPL).registerListener(new CommandQueueListener(manager));
        ((AbstractModule) IMPL).scheduleRepeating(manager::dispatchDue, 1L, Math.max(1, config.pollIntervalSeconds), TimeUnit.SECONDS);
        AbstractModule.registerCommand("tqueue", "queue", new CommandQueueCommand(manager));
        manager.dispatchDue();
    }

    private static void reloadImpl() {
        disableImpl();
        enableImpl();
    }

    private static void disableImpl() {
        AbstractModule.unregisterCommands("tqueue", "queue");
        if (manager != null) {
            manager.close();
            manager = null;
        }
    }
}
