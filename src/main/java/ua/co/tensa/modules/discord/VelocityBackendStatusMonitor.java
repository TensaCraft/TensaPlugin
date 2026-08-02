package ua.co.tensa.modules.discord;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class VelocityBackendStatusMonitor implements AutoCloseable {
    private final ProxyServer proxy;
    private final DiscordSettings settings;
    private final DiscordRuntime runtime;
    private final DiscordServerPolicy servers;
    private final BackendStatusDeduplicator states;
    private final AtomicBoolean polling = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    VelocityBackendStatusMonitor(ProxyServer proxy, DiscordSettings settings, DiscordRuntime runtime) {
        this.proxy = proxy;
        this.settings = settings;
        this.runtime = runtime;
        this.servers = runtime.serverPolicy();
        this.states = new BackendStatusDeduplicator(
                settings.backendStatusDebounce(),
                settings.backendStatusConfirmations(),
                settings.maxMonitoredServers()
        );
    }

    void poll() {
        if (closed.get() || !polling.compareAndSet(false, true)) {
            return;
        }
        Collection<RegisteredServer> registered = proxy.getAllServers();
        Map<String, RegisteredServer> byName = new LinkedHashMap<>();
        registered.forEach(server -> byName.put(
                server.getServerInfo().getName().toLowerCase(java.util.Locale.ROOT), server));
        List<String> targets = servers.monitorTargets(byName.values().stream()
                .map(server -> server.getServerInfo().getName())
                .toList());
        if (targets.isEmpty()) {
            polling.set(false);
            return;
        }

        Map<String, Boolean> results = new ConcurrentHashMap<>();
        CompletableFuture<?>[] probes = targets.stream()
                .map(name -> probe(name, byName.get(name.toLowerCase(java.util.Locale.ROOT)), results))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(probes).whenComplete((ignored, error) -> {
            try {
                if (!closed.get()) {
                    Instant observedAt = Instant.now();
                    targets.forEach(name -> publishTransition(
                            name,
                            results.getOrDefault(name, false),
                            observedAt
                    ));
                }
            } finally {
                polling.set(false);
            }
        });
    }

    private CompletableFuture<Void> probe(
            String name,
            RegisteredServer server,
            Map<String, Boolean> results
    ) {
        if (server == null) {
            results.put(name, false);
            return CompletableFuture.completedFuture(null);
        }
        return server.ping()
                .orTimeout(settings.backendStatusPingTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .handle((ping, error) -> {
                    results.put(name, error == null);
                    return null;
                });
    }

    private void publishTransition(String serverName, boolean available, Instant observedAt) {
        states.observe(serverName, available, observedAt).ifPresent(transition -> {
            if (transition == BackendStatusDeduplicator.Transition.RECOVERED) {
                runtime.announceBackendRecovered(serverName);
            } else {
                runtime.announceBackendUnavailable(serverName);
            }
        });
    }

    @Override
    public void close() {
        closed.set(true);
    }
}
