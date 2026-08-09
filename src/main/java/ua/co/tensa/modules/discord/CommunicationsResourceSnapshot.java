package ua.co.tensa.modules.discord;

/** Safe, runtime-only resource counts. Contains no identities or message data. */
public record CommunicationsResourceSnapshot(
        long heapUsedBytes,
        long heapMaxBytes,
        long heapDeltaBytes,
        int chatStateEntries,
        int privateReplyEntries,
        int linkCodes,
        int linkIndexEntries,
        int guardFeedbackEntries,
        int schedulerJobs,
        int schedulerQueueDepth,
        long schedulerRejected,
        long clickableUrlsRendered
) {
    static CommunicationsResourceSnapshot empty() {
        return new CommunicationsResourceSnapshot(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public int heapPercent() {
        return heapMaxBytes <= 0 ? 0 : Math.toIntExact(Math.min(100L, heapUsedBytes * 100L / heapMaxBytes));
    }
}
