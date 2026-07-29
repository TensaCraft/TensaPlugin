package ua.co.tensa.modules.authbridge;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import ua.co.tensa.Message;
import ua.co.tensa.authbridge.protocol.AuthState;
import xyz.kyngs.librelogin.api.LibreLoginPlugin;
import xyz.kyngs.librelogin.api.event.Event;
import xyz.kyngs.librelogin.api.event.EventType;
import xyz.kyngs.librelogin.api.event.PlayerBasedEvent;
import xyz.kyngs.librelogin.api.event.ServerChooseEvent;
import xyz.kyngs.librelogin.api.provider.LibreLoginProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class LibreLoginAuthSource implements AuthenticationStateSource {
    private final LibreLoginPlugin<Player, RegisteredServer> libreLogin;
    private final AliasReconnectPolicy aliasReconnectPolicy;
    private final List<Consumer<? extends Event<Player, RegisteredServer>>> subscriptions =
            new ArrayList<>();

    static LibreLoginAuthSource open(
            ProxyServer server,
            boolean suppressSameBackendAliasReconnect,
            List<String> allowedServers,
            Map<String, String> sourceBindings
    ) {
        PluginContainer container = server.getPluginManager()
                .getPlugin("librelogin")
                .orElseThrow(() -> new IllegalStateException("LibreLogin is not installed"));
        Object instance = container.getInstance()
                .orElseThrow(() -> new IllegalStateException("LibreLogin plugin instance is unavailable"));
        if (!(instance instanceof LibreLoginProvider<?, ?> rawProvider)) {
            throw new IllegalStateException("LibreLogin does not expose LibreLoginProvider");
        }

        @SuppressWarnings("unchecked")
        LibreLoginProvider<Player, RegisteredServer> provider =
                (LibreLoginProvider<Player, RegisteredServer>) rawProvider;
        LibreLoginPlugin<Player, RegisteredServer> plugin = provider.getLibreLogin();
        if (plugin == null || plugin.getAuthorizationProvider() == null || plugin.getEventProvider() == null) {
            throw new IllegalStateException("LibreLogin API is not initialized");
        }
        return new LibreLoginAuthSource(
                plugin,
                suppressSameBackendAliasReconnect,
                allowedServers,
                sourceBindings
        );
    }

    LibreLoginAuthSource(LibreLoginPlugin<Player, RegisteredServer> libreLogin) {
        this(libreLogin, false, List.of(), Map.of());
    }

    LibreLoginAuthSource(
            LibreLoginPlugin<Player, RegisteredServer> libreLogin,
            boolean suppressSameBackendAliasReconnect,
            List<String> allowedServers,
            Map<String, String> sourceBindings
    ) {
        this.libreLogin = Objects.requireNonNull(libreLogin, "libreLogin");
        this.aliasReconnectPolicy = AliasReconnectPolicy.create(
                suppressSameBackendAliasReconnect,
                allowedServers,
                sourceBindings
        );
    }

    @Override
    public AuthState currentState(Player player) {
        var authorization = libreLogin.getAuthorizationProvider();
        if (authorization.isAwaiting2FA(player)) {
            return AuthState.AWAITING_SECOND_FACTOR;
        }
        return authorization.isAuthorized(player)
                ? AuthState.AUTHORIZED
                : AuthState.PENDING;
    }

    @Override
    public synchronized void subscribeStateChanges(Consumer<Player> listener) {
        Objects.requireNonNull(listener, "listener");
        unsubscribeAll();
        var types = libreLogin.getEventTypes();
        subscribe(types.authenticated, listener);
        subscribe(types.wrongPassword, listener);
        subscribe(types.premiumLoginSwitch, listener);
        if (aliasReconnectPolicy.enabled()) {
            subscribeServerChoice(
                    types.lobbyServerChoose,
                    () -> libreLogin.getServerHandler().getLobbyServers().values()
            );
            subscribeServerChoice(
                    types.limboServerChoose,
                    () -> libreLogin.getServerHandler().getLimboServers()
            );
        }
    }

    private <E extends PlayerBasedEvent<Player, RegisteredServer>> void subscribe(
            EventType<Player, RegisteredServer, E> type,
            Consumer<Player> listener
    ) {
        Consumer<E> handler = event -> {
            try {
                Player player = event.getPlayer();
                if (player != null) {
                    listener.accept(player);
                }
            } catch (Throwable throwable) {
                Message.warn("LibreLogin auth bridge event failed: " + safeMessage(throwable));
            }
        };
        subscriptions.add(libreLogin.getEventProvider().subscribe(type, handler));
    }

    private <E extends ServerChooseEvent<Player, RegisteredServer>> void subscribeServerChoice(
            EventType<Player, RegisteredServer, E> type,
            Supplier<Collection<RegisteredServer>> configuredCandidates
    ) {
        Consumer<E> handler = event -> {
            try {
                RegisteredServer currentServer = currentServer(event.getPlayer());
                RegisteredServer selectedServer = event.getServer();
                Collection<RegisteredServer> candidates = selectedServer == null
                        ? configuredCandidates.get()
                        : List.of();
                if (aliasReconnectPolicy.shouldKeepCurrent(
                        currentServer,
                        selectedServer,
                        candidates
                )) {
                    event.setServer(currentServer);
                }
            } catch (Throwable throwable) {
                Message.warn(
                        "LibreLogin auth bridge server choice event failed: "
                                + safeMessage(throwable)
                );
            }
        };
        subscriptions.add(libreLogin.getEventProvider().subscribe(type, handler));
    }

    private static RegisteredServer currentServer(Player player) {
        if (player == null) {
            return null;
        }
        return player.getCurrentServer()
                .map(ServerConnection::getServer)
                .orElse(null);
    }

    @Override
    public synchronized void close() {
        unsubscribeAll();
    }

    private void unsubscribeAll() {
        if (subscriptions.isEmpty()) {
            return;
        }
        for (Consumer<? extends Event<Player, RegisteredServer>> subscription
                : List.copyOf(subscriptions)) {
            try {
                libreLogin.getEventProvider().unsubscribe(subscription);
            } catch (Throwable throwable) {
                Message.warn("LibreLogin auth bridge unsubscribe failed: " + safeMessage(throwable));
            }
        }
        subscriptions.clear();
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    private record AliasReconnectPolicy(
            boolean enabled,
            Set<String> allowedServers,
            Map<String, String> sourceBindings
    ) {
        private static AliasReconnectPolicy create(
                boolean enabled,
                List<String> configuredServers,
                Map<String, String> configuredBindings
        ) {
            if (!enabled) {
                return new AliasReconnectPolicy(false, Set.of(), Map.of());
            }

            Set<String> allowedServers = new HashSet<>();
            if (configuredServers != null) {
                for (String server : configuredServers) {
                    if (server != null && !server.isBlank()) {
                        allowedServers.add(normalizeServerName(server));
                    }
                }
            }
            if (allowedServers.isEmpty()) {
                throw new IllegalStateException(
                        "Auth bridge allow_from must contain at least one backend server"
                );
            }

            Map<String, String> sourceBindings = new HashMap<>();
            if (configuredBindings != null) {
                configuredBindings.forEach((server, backendId) -> {
                    if (server == null
                            || server.isBlank()
                            || backendId == null
                            || backendId.isBlank()) {
                        throw new IllegalStateException(
                                "Auth bridge source_bindings contains a blank key or value"
                        );
                    }
                    String source = normalizeServerName(server);
                    if (sourceBindings.putIfAbsent(source, backendId.trim()) != null) {
                        throw new IllegalStateException(
                                "Duplicate source binding for server '" + source + "'"
                        );
                    }
                });
            }
            if (!sourceBindings.keySet().equals(allowedServers)) {
                throw new IllegalStateException(
                        "Auth bridge source_bindings keys must exactly match allow_from server names"
                );
            }
            return new AliasReconnectPolicy(
                    true,
                    Set.copyOf(allowedServers),
                    Map.copyOf(sourceBindings)
            );
        }

        private boolean shouldKeepCurrent(
                RegisteredServer currentServer,
                RegisteredServer selectedServer,
                Collection<RegisteredServer> configuredCandidates
        ) {
            if (!enabled || currentServer == null) {
                return false;
            }

            String currentName = normalizeServerName(currentServer.getServerInfo().getName());
            if (!allowedServers.contains(currentName)) {
                return false;
            }
            String currentBackendId = sourceBindings.get(currentName);
            if (currentBackendId == null) {
                return false;
            }

            if (selectedServer != null) {
                String selectedName =
                        normalizeServerName(selectedServer.getServerInfo().getName());
                if (currentName.equals(selectedName) || !allowedServers.contains(selectedName)) {
                    return false;
                }
                return currentBackendId.equals(sourceBindings.get(selectedName));
            }

            if (configuredCandidates == null) {
                return false;
            }
            List<RegisteredServer> candidates = List.copyOf(configuredCandidates);
            if (candidates.isEmpty()) {
                return false;
            }
            for (RegisteredServer candidate : candidates) {
                String candidateName =
                        normalizeServerName(candidate.getServerInfo().getName());
                if (!allowedServers.contains(candidateName)
                        || !currentBackendId.equals(sourceBindings.get(candidateName))) {
                    return false;
                }
            }
            return true;
        }

        private static String normalizeServerName(String value) {
            return value.trim().toLowerCase(Locale.ROOT);
        }
    }
}
