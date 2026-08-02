package ua.co.tensa.modules.queue;

import ua.co.tensa.modules.queue.data.CommandQueueConfig;

record CommandQueueSettings(
        int pollIntervalSeconds,
        int maxEntries,
        int maxDispatchPerSweep,
        boolean requireServerConnection,
        boolean logDispatch
) {
    static CommandQueueSettings from(CommandQueueConfig config) {
        if (config.pollIntervalSeconds < 1 || config.pollIntervalSeconds > 3_600) {
            throw new IllegalStateException("poll_interval_seconds must be between 1 and 3600");
        }
        if (config.maxEntries < 1 || config.maxEntries > 100_000) {
            throw new IllegalStateException("max_entries must be between 1 and 100000");
        }
        if (config.maxDispatchPerSweep < 1 || config.maxDispatchPerSweep > 1_000) {
            throw new IllegalStateException("max_dispatch_per_sweep must be between 1 and 1000");
        }
        return new CommandQueueSettings(
                config.pollIntervalSeconds,
                config.maxEntries,
                config.maxDispatchPerSweep,
                config.requireServerConnection,
                config.logDispatch
        );
    }
}
