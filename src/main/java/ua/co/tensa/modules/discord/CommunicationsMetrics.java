package ua.co.tensa.modules.discord;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

/** Runtime-only counters owned by Communications across targeted reloads. */
public final class CommunicationsMetrics {
    private final LongAdder drops = new LongAdder();
    private final LongAdder retries = new LongAdder();
    private final LongAdder deliveryFailures = new LongAdder();
    private final LongAdder reconnects = new LongAdder();
    private final AtomicLong lastLatencyNanos = new AtomicLong();
    private final AtomicReference<String> lastFailureClass = new AtomicReference<>("none");
    private final AtomicReference<CommunicationsResourceSnapshot> resources =
            new AtomicReference<>(CommunicationsResourceSnapshot.empty());

    void dropped() { drops.increment(); }
    void retried() { retries.increment(); }
    void reconnectAttempted() { reconnects.increment(); }

    void deliveryFailed(Throwable failure) {
        deliveryFailures.increment();
        recordFailure(failure);
    }

    void observedLatency(long startedNanos) {
        if (startedNanos > 0L) {
            lastLatencyNanos.set(Math.max(0L, System.nanoTime() - startedNanos));
        }
    }

    void recordFailure(Throwable failure) {
        Throwable safe = DiscordDiagnostics.unwrap(failure);
        lastFailureClass.set(safe == null ? "unknown" : safe.getClass().getSimpleName());
    }

    void observeResources(CommunicationsResourceSnapshot snapshot) {
        resources.set(snapshot == null ? CommunicationsResourceSnapshot.empty() : snapshot);
    }

    Snapshot snapshot(
            String runtimeState,
            String jdaState,
            String slashState,
            String chatTransportState,
            String storageBackend,
            int inboundQueueDepth,
            int outboundQueueDepth,
            int roleQueueDepth,
            long reconnects
    ) {
        return new Snapshot(
                runtimeState, jdaState, slashState, chatTransportState, storageBackend,
                inboundQueueDepth, outboundQueueDepth, roleQueueDepth,
                drops.sum(), retries.sum(), deliveryFailures.sum(), reconnects + this.reconnects.sum(),
                Duration.ofNanos(lastLatencyNanos.get()).toMillis(), lastFailureClass.get(), resources.get()
        );
    }

    public record Snapshot(
            String runtimeState,
            String jdaState,
            String slashState,
            String chatTransportState,
            String storageBackend,
            int inboundQueueDepth,
            int outboundQueueDepth,
            int roleQueueDepth,
            long drops,
            long retries,
            long deliveryFailures,
            long reconnects,
            long lastObservedLatencyMillis,
            String lastFailureClass,
            CommunicationsResourceSnapshot resources
    ) { }
}
