package ua.co.tensa.authbridge.protocol.security;

import ua.co.tensa.authbridge.protocol.AuthFrame;

import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class ReplayWindow {
    private final int capacity;
    private final Map<UUID, Long> messageExpirations = new HashMap<>();
    private final Map<String, Long> nonceExpirations = new HashMap<>();
    private final Map<SessionKey, Long> sequences = new HashMap<>();

    public ReplayWindow(int capacity) {
        if (capacity < 128) {
            throw new IllegalArgumentException("capacity must be at least 128");
        }
        this.capacity = capacity;
    }

    public synchronized ReplayDecision checkAndRecord(AuthFrame frame, long nowEpochMillis) {
        return checkAndRecord(frame, nowEpochMillis, true);
    }

    synchronized ReplayDecision checkAndRecord(
            AuthFrame frame,
            long nowEpochMillis,
            boolean requireIncreasingSequence
    ) {
        purgeExpired(nowEpochMillis);

        String nonce = Base64.getEncoder().withoutPadding().encodeToString(frame.nonce());
        SessionKey sessionKey = new SessionKey(frame.playerId(), frame.sessionId(), frame.backendId());
        Long sequence = sequences.get(sessionKey);

        if (messageExpirations.containsKey(frame.messageId())) {
            return ReplayDecision.DUPLICATE_MESSAGE;
        }
        if (nonceExpirations.containsKey(nonce)) {
            return ReplayDecision.DUPLICATE_NONCE;
        }
        if (sequence != null && (requireIncreasingSequence
                ? frame.sequence() <= sequence
                : frame.sequence() < sequence)) {
            return ReplayDecision.STALE_SEQUENCE;
        }
        if (messageExpirations.size() >= capacity
                || nonceExpirations.size() >= capacity
                || sequences.size() >= capacity) {
            return ReplayDecision.CAPACITY_EXCEEDED;
        }

        messageExpirations.put(frame.messageId(), frame.expiresAtEpochMillis());
        nonceExpirations.put(nonce, frame.expiresAtEpochMillis());
        sequences.put(sessionKey, frame.sequence());
        return ReplayDecision.ACCEPTED;
    }

    public synchronized void forgetSession(UUID playerId, UUID sessionId, String backendId) {
        sequences.remove(new SessionKey(playerId, sessionId, backendId));
    }

    public synchronized void clear() {
        messageExpirations.clear();
        nonceExpirations.clear();
        sequences.clear();
    }

    private void purgeExpired(long nowEpochMillis) {
        removeExpired(messageExpirations, nowEpochMillis);
        removeExpired(nonceExpirations, nowEpochMillis);
    }

    private static <K> void removeExpired(Map<K, Long> values, long nowEpochMillis) {
        Iterator<Map.Entry<K, Long>> iterator = values.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue() <= nowEpochMillis) {
                iterator.remove();
            }
        }
    }

    private record SessionKey(UUID playerId, UUID sessionId, String backendId) {
    }
}
