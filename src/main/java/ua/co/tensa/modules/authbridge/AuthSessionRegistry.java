package ua.co.tensa.modules.authbridge;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import ua.co.tensa.authbridge.protocol.AuthState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

final class AuthSessionRegistry {
    private final ConcurrentHashMap<UUID, Session> sessions = new ConcurrentHashMap<>();

    Binding begin(Player player) {
        Session session = new Session(player);
        Session previous = sessions.put(player.getUniqueId(), session);
        return previous == null ? null : previous.binding();
    }

    Snapshot ensure(Player player) {
        Session session = sessions.compute(player.getUniqueId(), (ignored, existing) -> {
            if (existing == null || existing.player != player) {
                return new Session(player);
            }
            return existing;
        });
        return session.snapshot();
    }

    Snapshot update(Player player, AuthState state) {
        AtomicReference<Snapshot> updated = new AtomicReference<>();
        sessions.computeIfPresent(player.getUniqueId(), (ignored, existing) -> {
            if (existing.player == player) {
                updated.set(existing.update(state));
            }
            return existing;
        });
        return updated.get();
    }

    BindResult bind(
            Player player,
            ServerConnection connection,
            String sourceServer,
            String backendId,
            UUID sessionId,
            byte[] challenge
    ) {
        AtomicReference<BindResult> result = new AtomicReference<>();
        sessions.compute(player.getUniqueId(), (ignored, existing) -> {
            Session session = existing;
            if (session == null) {
                session = new Session(player);
            } else if (session.player != player) {
                return session;
            }
            result.set(session.bind(connection, sourceServer, backendId, sessionId, challenge));
            return session;
        });
        return result.get();
    }

    Snapshot next(Player player, ServerConnection connection) {
        return next(player, connection, false);
    }

    Snapshot nextIfStateChanged(Player player, ServerConnection connection) {
        return next(player, connection, true);
    }

    private Snapshot next(Player player, ServerConnection connection, boolean stateChangeOnly) {
        AtomicReference<Snapshot> next = new AtomicReference<>();
        sessions.computeIfPresent(player.getUniqueId(), (ignored, existing) -> {
            if (existing.player == player) {
                next.set(existing.next(connection, stateChangeOnly));
            }
            return existing;
        });
        return next.get();
    }

    boolean markPublished(Player player, Snapshot snapshot) {
        AtomicBoolean transition = new AtomicBoolean();
        sessions.computeIfPresent(player.getUniqueId(), (ignored, existing) -> {
            if (existing.player == player) {
                transition.set(existing.markPublished(snapshot));
            }
            return existing;
        });
        return transition.get();
    }

    boolean hasActiveBinding(Player player, ServerConnection connection) {
        Session session = sessions.get(player.getUniqueId());
        return session != null && session.hasActiveBinding(player, connection);
    }

    Binding remove(Player player) {
        AtomicReference<Binding> removed = new AtomicReference<>();
        sessions.computeIfPresent(player.getUniqueId(), (ignored, existing) -> {
            if (existing.player != player) {
                return existing;
            }
            removed.set(existing.binding);
            return null;
        });
        return removed.get();
    }

    List<Binding> clear() {
        List<Binding> bindings = new ArrayList<>();
        sessions.forEach((ignored, session) -> {
            if (session.binding != null) {
                bindings.add(session.binding);
            }
        });
        sessions.clear();
        return List.copyOf(bindings);
    }

    record Snapshot(AuthState state, Binding binding, long sequence) {
    }

    record BindResult(Snapshot snapshot, Binding previousBinding) {
    }

    record Binding(
            UUID playerId,
            ServerConnection connection,
            String sourceServer,
            String backendId,
            UUID sessionId,
            byte[] challenge
    ) {
        Binding {
            challenge = Arrays.copyOf(challenge, challenge.length);
        }

        @Override
        public byte[] challenge() {
            return challenge.clone();
        }
    }

    private static final class Session {
        private final Player player;
        private AuthState state = AuthState.PENDING;
        private Binding binding;
        private long sequence;
        private AuthState lastPublishedState;
        private long lastPublishedSequence;

        private Session(Player player) {
            this.player = player;
        }

        private synchronized Snapshot snapshot() {
            return new Snapshot(state, binding, sequence);
        }

        private synchronized Binding binding() {
            return binding;
        }

        private synchronized Snapshot update(AuthState state) {
            this.state = state;
            return snapshot();
        }

        private synchronized BindResult bind(
                ServerConnection connection,
                String sourceServer,
                String backendId,
                UUID sessionId,
                byte[] challenge
        ) {
            Binding previous = binding;
            binding = new Binding(
                    player.getUniqueId(),
                    connection,
                    sourceServer,
                    backendId,
                    sessionId,
                    challenge
            );
            sequence = 0L;
            lastPublishedState = null;
            lastPublishedSequence = 0L;
            return new BindResult(snapshot(), previous);
        }

        private synchronized Snapshot next(
                ServerConnection connection,
                boolean stateChangeOnly
        ) {
            if (binding == null || binding.connection() != connection) {
                return null;
            }
            if (stateChangeOnly && state == lastPublishedState) {
                return null;
            }
            sequence++;
            return snapshot();
        }

        private synchronized boolean markPublished(Snapshot published) {
            if (published == null
                    || published.binding() != binding
                    || published.sequence() <= lastPublishedSequence) {
                return false;
            }
            boolean transition = lastPublishedState != published.state();
            lastPublishedState = published.state();
            lastPublishedSequence = published.sequence();
            return transition;
        }

        private synchronized boolean hasActiveBinding(Player expectedPlayer, ServerConnection connection) {
            return player == expectedPlayer
                    && binding != null
                    && binding.connection() == connection;
        }
    }
}
