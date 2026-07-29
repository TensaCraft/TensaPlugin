package ua.co.tensa.authbridge.protocol;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class AuthBridgeReplayGuard {
    private static final int CLEANUP_INTERVAL = 256;

    private final ConcurrentHashMap<ReplayKey, ReplayEntry> entries = new ConcurrentHashMap<>();
    private final AtomicInteger operations = new AtomicInteger();

    public boolean accept(String peer, AuthBridgeMessage message, long nowEpochMillis) {
        Objects.requireNonNull(peer, "peer");
        Objects.requireNonNull(message, "message");
        ReplayKey key = new ReplayKey(
                peer,
                message.playerId(),
                message.sessionId(),
                message.type()
        );
        boolean[] accepted = {false};
        entries.compute(key, (ignored, existing) -> {
            if (existing == null || existing.expiresAt < nowEpochMillis || message.sequence() > existing.sequence) {
                accepted[0] = true;
                return new ReplayEntry(message.sequence(), message.expiresAt());
            }
            return existing;
        });
        if ((operations.incrementAndGet() & (CLEANUP_INTERVAL - 1)) == 0) {
            entries.entrySet().removeIf(entry -> entry.getValue().expiresAt < nowEpochMillis);
        }
        return accepted[0];
    }

    public void clear() {
        entries.clear();
    }

    private record ReplayKey(
            String peer,
            java.util.UUID playerId,
            java.util.UUID sessionId,
            AuthBridgeMessageType type
    ) {
    }

    private record ReplayEntry(long sequence, long expiresAt) {
    }
}
