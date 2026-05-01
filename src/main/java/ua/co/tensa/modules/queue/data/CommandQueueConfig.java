package ua.co.tensa.modules.queue.data;

import ua.co.tensa.config.model.ConfigBase;
import ua.co.tensa.config.model.ann.CfgKey;

public final class CommandQueueConfig extends ConfigBase {
    private static CommandQueueConfig instance;

    @CfgKey(value = "poll_interval_seconds", comment = "How often the queue scans for due commands that can be delivered")
    public int pollIntervalSeconds = 2;

    @CfgKey(value = "max_entries", comment = "Maximum number of queued commands stored at once")
    public int maxEntries = 5000;

    @CfgKey(value = "max_dispatch_per_sweep", comment = "Maximum number of queued commands dispatched in one scan cycle")
    public int maxDispatchPerSweep = 50;

    @CfgKey(value = "require_server_connection", comment = "Only dispatch when the target player is connected to a backend server")
    public boolean requireServerConnection = true;

    @CfgKey(value = "log_dispatch", comment = "Write queued command creation, dispatch start, and dispatch completion to the console log")
    public boolean logDispatch = true;

    private CommandQueueConfig() {
        super("queue/config.yml");
    }

    public static synchronized CommandQueueConfig get() {
        if (instance == null) {
            instance = new CommandQueueConfig();
        }
        return instance;
    }
}
