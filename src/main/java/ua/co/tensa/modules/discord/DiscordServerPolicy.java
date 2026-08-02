package ua.co.tensa.modules.discord;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class DiscordServerPolicy {
    private final List<String> included;
    private final Set<String> excluded;
    private final Map<String, String> labels;
    private final int maxMonitoredServers;

    DiscordServerPolicy(DiscordSettings settings) {
        this.included = settings.includedServers();
        this.excluded = settings.excludedServers();
        this.labels = settings.serverLabels();
        this.maxMonitoredServers = settings.maxMonitoredServers();
    }

    boolean includes(String serverName) {
        String normalized = normalize(serverName);
        return !normalized.isBlank()
                && !excluded.contains(normalized)
                && (included.isEmpty() || included.contains(normalized));
    }

    String label(String serverName) {
        String normalized = normalize(serverName);
        return labels.getOrDefault(normalized, serverName == null ? "" : serverName.trim());
    }

    List<String> monitorTargets(Collection<String> registeredServers) {
        Map<String, String> registered = new LinkedHashMap<>();
        if (registeredServers != null) {
            registeredServers.stream()
                    .filter(this::includes)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(name -> registered.putIfAbsent(normalize(name), name));
        }

        List<String> targets = new ArrayList<>();
        Collection<String> candidates = included.isEmpty() ? registered.values() : included;
        for (String candidate : candidates) {
            String normalized = normalize(candidate);
            if (excluded.contains(normalized)) {
                continue;
            }
            targets.add(registered.getOrDefault(normalized, candidate));
            if (targets.size() >= maxMonitoredServers) {
                break;
            }
        }
        return List.copyOf(targets);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
