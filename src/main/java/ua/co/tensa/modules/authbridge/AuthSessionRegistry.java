package ua.co.tensa.modules.authbridge;

import com.velocitypowered.api.proxy.Player;
import ua.co.tensa.authbridge.protocol.AuthBridgeState;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

final class AuthSessionRegistry {
    private final ConcurrentHashMap<UUID, Session> sessions = new ConcurrentHashMap<>();

    Snapshot begin(Player player) {
        Session session = new Session(player, UUID.randomUUID());
        sessions.put(player.getUniqueId(), session);
        return session.snapshot();
    }

    Snapshot ensure(Player player) {
        Session session = sessions.compute(player.getUniqueId(), (ignored, existing) -> {
            if (existing == null || existing.player != player) {
                return new Session(player, UUID.randomUUID());
            }
            return existing;
        });
        return session.snapshot();
    }

    Snapshot update(Player player, AuthBridgeState state) {
        java.util.concurrent.atomic.AtomicReference<Snapshot> updated = new java.util.concurrent.atomic.AtomicReference<>();
        sessions.computeIfPresent(player.getUniqueId(), (ignored, existing) -> {
            if (existing.player == player) {
                existing.state = state;
                updated.set(existing.snapshot());
            }
            return existing;
        });
        return updated.get();
    }

    Snapshot next(Player player) {
        java.util.concurrent.atomic.AtomicReference<Snapshot> next = new java.util.concurrent.atomic.AtomicReference<>();
        sessions.computeIfPresent(player.getUniqueId(), (ignored, existing) -> {
            if (existing.player == player) {
                next.set(existing.nextSnapshot());
            }
            return existing;
        });
        return next.get();
    }

    void remove(Player player) {
        sessions.computeIfPresent(player.getUniqueId(), (ignored, existing) ->
                existing.player == player ? null : existing);
    }

    void clear() {
        sessions.clear();
    }

    record Snapshot(UUID sessionId, long sequence, AuthBridgeState state) {
    }

    private static final class Session {
        private final Player player;
        private final UUID sessionId;
        private final AtomicLong sequence = new AtomicLong();
        private volatile AuthBridgeState state = AuthBridgeState.LOCKED;

        private Session(Player player, UUID sessionId) {
            this.player = player;
            this.sessionId = sessionId;
        }

        private Snapshot snapshot() {
            return new Snapshot(sessionId, sequence.get(), state);
        }

        private Snapshot nextSnapshot() {
            return new Snapshot(sessionId, sequence.incrementAndGet(), state);
        }
    }
}
