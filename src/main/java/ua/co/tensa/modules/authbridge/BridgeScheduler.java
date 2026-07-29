package ua.co.tensa.modules.authbridge;

interface BridgeScheduler {
    void execute(Runnable task);

    void delayed(Runnable task, long delayMillis);
}
