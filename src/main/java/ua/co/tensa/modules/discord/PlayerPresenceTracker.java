package ua.co.tensa.modules.discord;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class PlayerPresenceTracker {
    enum Type {
        JOIN,
        QUIT,
        SWITCH
    }

    record Transition(Type type, String fromServer, String toServer) {
    }

    private final DiscordServerPolicy policy;
    private final int capacity;
    private final Map<UUID, String> currentServers = new LinkedHashMap<>(16, 0.75f, true);

    PlayerPresenceTracker(DiscordServerPolicy policy, int capacity) {
        this.policy = policy;
        this.capacity = capacity;
    }

    synchronized void prime(UUID playerUuid, String serverName) {
        if (playerUuid != null && policy.includes(serverName)) {
            remember(playerUuid, serverName);
        }
    }

    synchronized Optional<Transition> connected(UUID playerUuid, String serverName) {
        if (playerUuid == null || !policy.includes(serverName)) {
            return Optional.empty();
        }
        String previous = currentServers.get(playerUuid);
        remember(playerUuid, serverName);
        if (previous == null) {
            return Optional.of(new Transition(Type.JOIN, "", serverName));
        }
        if (previous.equalsIgnoreCase(serverName)) {
            return Optional.empty();
        }
        return Optional.of(new Transition(Type.SWITCH, previous, serverName));
    }

    synchronized Optional<Transition> disconnected(UUID playerUuid) {
        if (playerUuid == null) {
            return Optional.empty();
        }
        String server = currentServers.remove(playerUuid);
        if (server == null || !policy.includes(server)) {
            return Optional.empty();
        }
        return Optional.of(new Transition(Type.QUIT, server, ""));
    }

    synchronized int size() {
        return currentServers.size();
    }

    private void remember(UUID playerUuid, String serverName) {
        currentServers.put(playerUuid, serverName);
        while (currentServers.size() > capacity) {
            UUID eldest = currentServers.keySet().iterator().next();
            currentServers.remove(eldest);
        }
    }
}
