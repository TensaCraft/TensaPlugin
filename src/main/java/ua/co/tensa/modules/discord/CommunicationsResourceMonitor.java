package ua.co.tensa.modules.discord;

final class CommunicationsResourceMonitor {
    enum Transition { NONE, HIGH, RECOVERED }

    private final long baselineHeapBytes;
    private final int highPercent;
    private final int recoveredPercent;
    private boolean pressure;

    CommunicationsResourceMonitor(long baselineHeapBytes, int highPercent, int recoveredPercent) {
        if (highPercent <= recoveredPercent || highPercent > 100 || recoveredPercent < 0) {
            throw new IllegalArgumentException("Invalid heap alert thresholds");
        }
        this.baselineHeapBytes = Math.max(0L, baselineHeapBytes);
        this.highPercent = highPercent;
        this.recoveredPercent = recoveredPercent;
    }

    CommunicationsResourceSnapshot capture(
            int chatStates,
            int privateReplies,
            long clickableUrlsRendered,
            DiscordRuntime.ResourceState discord,
            ua.co.tensa.modules.runtime.ModuleScheduler.Snapshot scheduler
    ) {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        return new CommunicationsResourceSnapshot(
                used,
                runtime.maxMemory(),
                used - baselineHeapBytes,
                chatStates,
                privateReplies,
                discord.linkCodes(),
                discord.linkIndexEntries(),
                scheduler.activeJobs(),
                scheduler.workerQueueDepth(),
                scheduler.rejected(),
                clickableUrlsRendered
        );
    }

    synchronized Transition evaluate(CommunicationsResourceSnapshot snapshot) {
        int percentage = snapshot.heapPercent();
        if (!pressure && percentage >= highPercent) {
            pressure = true;
            return Transition.HIGH;
        }
        if (pressure && percentage <= recoveredPercent) {
            pressure = false;
            return Transition.RECOVERED;
        }
        return Transition.NONE;
    }
}
