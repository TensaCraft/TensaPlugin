package ua.co.tensa.modules.authbridge;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import ua.co.tensa.Message;
import ua.co.tensa.authbridge.protocol.AuthBridgeMessage;
import ua.co.tensa.authbridge.protocol.AuthBridgeMessageType;
import ua.co.tensa.authbridge.protocol.AuthBridgeMessageVerifier;
import ua.co.tensa.authbridge.protocol.AuthBridgeProtocol;
import ua.co.tensa.authbridge.protocol.AuthBridgeProtocolException;
import ua.co.tensa.authbridge.protocol.AuthBridgeReplayGuard;
import ua.co.tensa.authbridge.protocol.AuthBridgeState;

import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

final class AuthBridgeRuntime implements AutoCloseable {
    private static final long WARNING_INTERVAL_MILLIS = 5_000L;

    private final ProxyServer server;
    private final AuthenticationStateSource source;
    private final BridgeScheduler scheduler;
    private final AuthSessionRegistry sessions;
    private final AuthBridgeReplayGuard replayGuard;
    private final AuthBridgeMessageVerifier verifier;
    private final ChannelIdentifier channel;
    private final Set<String> allowedServers;
    private final byte[] secret;
    private final Clock clock;
    private final long messageTtlMillis;
    private final long postLoginDelayMillis;
    private final boolean logTransitions;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong lastWarningAt = new AtomicLong();

    AuthBridgeRuntime(
            ProxyServer server,
            AuthenticationStateSource source,
            BridgeScheduler scheduler,
            List<String> allowedServers,
            byte[] secret,
            Clock clock,
            Duration messageTtl,
            Duration clockSkew,
            long postLoginDelayMillis,
            boolean logTransitions
    ) {
        this.server = java.util.Objects.requireNonNull(server, "server");
        this.source = java.util.Objects.requireNonNull(source, "source");
        this.scheduler = java.util.Objects.requireNonNull(scheduler, "scheduler");
        this.allowedServers = normalizeAllowlist(allowedServers);
        this.secret = secret.clone();
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.messageTtlMillis = requirePositive(messageTtl.toMillis(), "messageTtl");
        this.postLoginDelayMillis = requireNonNegative(postLoginDelayMillis, "postLoginDelayMillis");
        this.logTransitions = logTransitions;
        this.sessions = new AuthSessionRegistry();
        this.replayGuard = new AuthBridgeReplayGuard();
        this.verifier = new AuthBridgeMessageVerifier(clock, messageTtl, clockSkew, replayGuard);
        this.channel = MinecraftChannelIdentifier.from(AuthBridgeProtocol.CHANNEL);
    }

    void start() {
        server.getChannelRegistrar().register(channel);
        List<Player> onlinePlayers = List.copyOf(server.getAllPlayers());
        for (Player player : onlinePlayers) {
            sessions.begin(player);
        }
        source.subscribeAuthenticated(this::onAuthenticated);
        for (Player player : onlinePlayers) {
            scheduleReconcileAndPublish(player, 0L);
        }
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        beginSession(event.getPlayer());
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        synchronizeAfterLogin(event.getPlayer());
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        resendToCurrentServer(event.getPlayer());
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        cleanupSession(event.getPlayer());
    }

    void beginSession(Player player) {
        if (!closed.get()) {
            sessions.begin(player);
        }
    }

    void synchronizeAfterLogin(Player player) {
        scheduleReconcileAndPublish(player, postLoginDelayMillis);
    }

    void resendToCurrentServer(Player player) {
        if (closed.get()) {
            return;
        }
        sessions.ensure(player);
        publishToCurrentServer(player, "");
    }

    void cleanupSession(Player player) {
        sessions.remove(player);
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(channel)) {
            return;
        }

        // Authentication traffic must never be forwarded to clients or other backends.
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (closed.get() || !(event.getSource() instanceof ServerConnection connection)) {
            return;
        }

        String serverName = connection.getServerInfo().getName();
        if (!allowedServers.contains(normalizeServerName(serverName))) {
            warnThrottled("Auth bridge rejected a message from disallowed server '" + serverName + "'");
            return;
        }

        try {
            AuthBridgeMessage query = AuthBridgeProtocol.decodeAndVerify(event.getData(), secret);
            validateQuery(connection, query);
            verifier.verifyFresh(normalizeServerName(serverName), query);
            Player player = connection.getPlayer();
            reconcile(player);
            sendState(connection, player, query.backendChallenge());
        } catch (AuthBridgeProtocolException | RuntimeException exception) {
            warnThrottled("Auth bridge rejected a message from '" + serverName + "': " + safeMessage(exception));
        } catch (Throwable throwable) {
            warnThrottled("Auth bridge message processing failed for '" + serverName + "': " + safeMessage(throwable));
        }
    }

    private void onAuthenticated(Player player) {
        try {
            scheduler.execute(() -> {
                try {
                    if (closed.get()) {
                        return;
                    }
                    reconcile(player);
                    publishToCurrentServer(player, "");
                } catch (Throwable throwable) {
                    warnThrottled("Auth bridge async publication failed: " + safeMessage(throwable));
                }
            });
        } catch (Throwable throwable) {
            warnThrottled("Auth bridge could not schedule authenticated event: " + safeMessage(throwable));
        }
    }

    private void scheduleReconcileAndPublish(Player player, long delayMillis) {
        if (closed.get()) {
            return;
        }
        try {
            scheduler.delayed(() -> {
                if (closed.get()) {
                    return;
                }
                reconcile(player);
                publishToCurrentServer(player, "");
            }, delayMillis);
        } catch (Throwable throwable) {
            warnThrottled("Auth bridge state synchronization could not be scheduled: " + safeMessage(throwable));
        }
    }

    private AuthBridgeState reconcile(Player player) {
        AuthBridgeState state = AuthBridgeState.LOCKED;
        try {
            state = source.currentState(player);
        } catch (Throwable throwable) {
            warnThrottled("LibreLogin state lookup failed for " + player.getUsername() + ": " + safeMessage(throwable));
        }
        sessions.update(player, state);
        return state;
    }

    private void publishToCurrentServer(Player player, String challenge) {
        player.getCurrentServer().ifPresent(connection -> sendState(connection, player, challenge));
    }

    private void sendState(ServerConnection connection, Player player, String challenge) {
        String serverName = connection.getServerInfo().getName();
        if (!allowedServers.contains(normalizeServerName(serverName))) {
            warnThrottled("Auth bridge refused to publish state to disallowed server '" + serverName + "'");
            return;
        }
        AuthSessionRegistry.Snapshot snapshot = sessions.next(player);
        if (snapshot == null) {
            return;
        }
        long issuedAt = clock.millis();
        long expiresAt = saturatedAdd(issuedAt, messageTtlMillis);
        AuthBridgeMessage response = new AuthBridgeMessage(
                AuthBridgeProtocol.VERSION,
                AuthBridgeMessageType.STATE,
                player.getUniqueId(),
                snapshot.sessionId(),
                challenge,
                snapshot.sequence(),
                issuedAt,
                expiresAt,
                snapshot.state()
        );

        try {
            byte[] payload = AuthBridgeProtocol.encode(response, secret);
            boolean sent = connection.sendPluginMessage(channel, payload);
            if (!sent) {
                warnThrottled("Auth bridge state could not be sent to '" + serverName + "'");
                return;
            }
            if (logTransitions) {
                Message.info(
                        "Auth bridge: "
                                + player.getUsername()
                                + " -> "
                                + snapshot.state()
                                + " on "
                                + serverName
                );
            }
        } catch (AuthBridgeProtocolException exception) {
            warnThrottled("Auth bridge state encoding failed: " + safeMessage(exception));
        }
    }

    private void validateQuery(ServerConnection connection, AuthBridgeMessage query)
            throws AuthBridgeProtocolException {
        if (query.type() != AuthBridgeMessageType.QUERY) {
            throw new AuthBridgeProtocolException("Only QUERY messages are accepted from backends");
        }
        if (query.state() != AuthBridgeState.LOCKED) {
            throw new AuthBridgeProtocolException("QUERY state must be LOCKED");
        }
        if (query.backendChallenge().isBlank()) {
            throw new AuthBridgeProtocolException("Backend challenge must not be empty");
        }
        if (query.backendChallenge().getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 16) {
            throw new AuthBridgeProtocolException("Backend challenge must contain at least 16 bytes");
        }

        Player player = connection.getPlayer();
        if (!query.playerId().equals(player.getUniqueId())) {
            throw new AuthBridgeProtocolException("Player UUID does not match the server connection");
        }
        AuthSessionRegistry.Snapshot current = sessions.ensure(player);
        if (!AuthBridgeProtocol.ZERO_SESSION.equals(query.sessionId())
                && !current.sessionId().equals(query.sessionId())) {
            throw new AuthBridgeProtocolException("Session does not match the current proxy connection");
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            source.close();
        } catch (Throwable throwable) {
            warnThrottled("LibreLogin auth bridge close failed: " + safeMessage(throwable));
        }
        try {
            server.getChannelRegistrar().unregister(channel);
        } catch (Throwable throwable) {
            warnThrottled("Auth bridge channel unregister failed: " + safeMessage(throwable));
        }
        sessions.clear();
        replayGuard.clear();
        java.util.Arrays.fill(secret, (byte) 0);
    }

    private void warnThrottled(String message) {
        long now = clock.millis();
        long previous = lastWarningAt.get();
        if (now - previous < WARNING_INTERVAL_MILLIS || !lastWarningAt.compareAndSet(previous, now)) {
            return;
        }
        Message.warn(message);
    }

    private static Set<String> normalizeAllowlist(List<String> configured) {
        Set<String> normalized = new HashSet<>();
        if (configured != null) {
            for (String server : configured) {
                if (server != null && !server.isBlank()) {
                    normalized.add(normalizeServerName(server));
                }
            }
        }
        if (normalized.isEmpty()) {
            throw new IllegalStateException("Auth bridge allow_from must contain at least one backend server");
        }
        return Set.copyOf(normalized);
    }

    private static String normalizeServerName(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static long requirePositive(long value, String name) {
        if (value <= 0L) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static long requireNonNegative(long value, String name) {
        if (value < 0L) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }

    private static long saturatedAdd(long value, long addend) {
        if (addend > 0L && value > Long.MAX_VALUE - addend) {
            return Long.MAX_VALUE;
        }
        return value + addend;
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }
}
