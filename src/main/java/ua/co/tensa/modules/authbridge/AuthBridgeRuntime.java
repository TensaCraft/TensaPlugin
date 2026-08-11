package ua.co.tensa.modules.authbridge;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import ua.co.tensa.Message;
import ua.co.tensa.authbridge.protocol.AuthFrame;
import ua.co.tensa.authbridge.protocol.AuthMessageType;
import ua.co.tensa.authbridge.protocol.AuthProtocolCodec;
import ua.co.tensa.authbridge.protocol.AuthProtocolException;
import ua.co.tensa.authbridge.protocol.AuthState;
import ua.co.tensa.authbridge.protocol.ProtocolConstants;
import ua.co.tensa.authbridge.protocol.security.AuthFrameVerifier;
import ua.co.tensa.authbridge.protocol.security.AuthSecurityPolicy;
import ua.co.tensa.authbridge.protocol.security.HmacSha256Authenticator;
import ua.co.tensa.authbridge.protocol.security.ReplayWindow;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

final class AuthBridgeRuntime implements AutoCloseable {
    private static final long WARNING_INTERVAL_MILLIS = 5_000L;

    private final ProxyServer server;
    private final AuthenticationStateSource source;
    private final BridgeScheduler scheduler;
    private final AuthSessionRegistry sessions = new AuthSessionRegistry();
    private final ReplayWindow replayWindow;
    private final AuthProtocolCodec codec = new AuthProtocolCodec();
    private final HmacSha256Authenticator authenticator;
    private final Map<String, AuthFrameVerifier> verifiers;
    private final ChannelIdentifier channel;
    private final Set<String> allowedServers;
    private final Map<String, String> sourceBindings;
    private final Clock clock;
    private final SecureRandom secureRandom;
    private final long messageTtlMillis;
    private final long postLoginDelayMillis;
    private final long heartbeatIntervalMillis;
    private final boolean logTransitions;
    private final Consumer<String> transitionLogger;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong lastWarningAt = new AtomicLong();
    private final Map<UUID, ExpectedServerConnection> expectedConnections = new ConcurrentHashMap<>();

    AuthBridgeRuntime(
            ProxyServer server,
            AuthenticationStateSource source,
            BridgeScheduler scheduler,
            List<String> allowedServers,
            Map<String, String> sourceBindings,
            byte[] secret,
            Clock clock,
            SecureRandom secureRandom,
            AuthSecurityPolicy securityPolicy,
            long postLoginDelayMillis,
            Duration heartbeatInterval,
            Duration authorizationLease,
            boolean logTransitions,
            Consumer<String> transitionLogger
    ) {
        this.server = java.util.Objects.requireNonNull(server, "server");
        this.source = java.util.Objects.requireNonNull(source, "source");
        this.scheduler = java.util.Objects.requireNonNull(scheduler, "scheduler");
        this.allowedServers = normalizeAllowlist(allowedServers);
        this.sourceBindings = normalizeSourceBindings(sourceBindings, this.allowedServers);
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.secureRandom = java.util.Objects.requireNonNull(secureRandom, "secureRandom");
        AuthSecurityPolicy policy = java.util.Objects.requireNonNull(securityPolicy, "securityPolicy");
        this.messageTtlMillis = policy.maximumFrameTtl().toMillis();
        this.postLoginDelayMillis = requireNonNegative(postLoginDelayMillis, "postLoginDelayMillis");
        this.heartbeatIntervalMillis = requireHeartbeatInterval(
                heartbeatInterval,
                authorizationLease
        );
        this.logTransitions = logTransitions;
        this.transitionLogger = java.util.Objects.requireNonNull(transitionLogger, "transitionLogger");
        this.replayWindow = new ReplayWindow(policy.replayCapacity());
        this.authenticator = new HmacSha256Authenticator(secret, codec);
        this.verifiers = createVerifiers(this.sourceBindings, policy, authenticator, replayWindow);
        this.channel = MinecraftChannelIdentifier.from(ProtocolConstants.CHANNEL);
    }

    void start() {
        server.getChannelRegistrar().register(channel);
        List<Player> onlinePlayers = List.copyOf(server.getAllPlayers());
        for (Player player : onlinePlayers) {
            forget(sessions.begin(player));
        }
        source.subscribeStateChanges(this::onAuthenticationStateChanged);
        for (Player player : onlinePlayers) {
            scheduleReconcileAndPublish(player, 0L);
        }
        scheduler.repeating(
                this::reconcileActiveSessions,
                heartbeatIntervalMillis,
                heartbeatIntervalMillis
        );
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        beginSession(event.getPlayer());
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        synchronizeAfterLogin(event.getPlayer());
    }

    @Subscribe(priority = Short.MIN_VALUE)
    public void onServerPreConnect(ServerPreConnectEvent event) {
        event.getResult().getServer().ifPresentOrElse(
                target -> expectServerConnection(event.getPlayer(), target.getServerInfo().getName()),
                () -> expectedConnections.remove(event.getPlayer().getUniqueId())
        );
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        expectedConnections.remove(event.getPlayer().getUniqueId());
        resendToCurrentServer(event.getPlayer());
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        cleanupSession(event.getPlayer());
    }

    void beginSession(Player player) {
        if (!closed.get()) {
            expectedConnections.remove(player.getUniqueId());
            forget(sessions.begin(player));
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
        player.getCurrentServer().ifPresent(connection -> sendState(connection, player));
    }

    void cleanupSession(Player player) {
        expectedConnections.remove(player.getUniqueId());
        forget(sessions.remove(player));
    }

    void expectServerConnection(Player player, String targetServer) {
        if (closed.get() || player == null || targetServer == null || targetServer.isBlank()) {
            return;
        }
        expectedConnections.put(
                player.getUniqueId(),
                new ExpectedServerConnection(
                        normalizeServerName(targetServer),
                        player.getCurrentServer().orElse(null),
                        saturatedAdd(clock.millis(), messageTtlMillis)
                )
        );
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(channel)) {
            return;
        }

        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (closed.get() || !(event.getSource() instanceof ServerConnection connection)) {
            return;
        }

        String sourceServer = normalizeServerName(connection.getServerInfo().getName());
        String expectedBackendId = sourceBindings.get(sourceServer);
        if (!allowedServers.contains(sourceServer) || expectedBackendId == null) {
            warnThrottled("Auth bridge rejected a message from disallowed server '" + sourceServer + "'");
            return;
        }

        try {
            AuthFrame challenge = codec.decode(event.getData());
            validateChallengeSource(connection, sourceServer, expectedBackendId, challenge);
            var verification = verifiers.get(expectedBackendId)
                    .verifyChallenge(challenge, clock.millis());
            if (!verification.accepted()) {
                throw new AuthProtocolException(
                        "Challenge verification failed: " + verification.failure().name().toLowerCase(Locale.ROOT)
                );
            }

            Player player = connection.getPlayer();
            AuthSessionRegistry.BindResult binding = sessions.bind(
                    player,
                    connection,
                    sourceServer,
                    expectedBackendId,
                    challenge.sessionId(),
                    challenge.challenge()
            );
            if (binding == null) {
                throw new AuthProtocolException("Challenge belongs to a stale player connection");
            }
            forgetIfReplaced(binding.previousBinding(), binding.snapshot().binding());
            reconcile(player);
            sendState(connection, player);
        } catch (RuntimeException exception) {
            warnThrottled("Auth bridge rejected a message from '" + sourceServer + "': " + safeMessage(exception));
        } catch (Throwable throwable) {
            warnThrottled("Auth bridge message processing failed for '" + sourceServer + "': " + safeMessage(throwable));
        }
    }

    private void onAuthenticationStateChanged(Player player) {
        try {
            scheduler.execute(() -> {
                try {
                    if (closed.get()) {
                        return;
                    }
                    reconcile(player);
                    publishStateChangeToCurrentServer(player);
                } catch (Throwable throwable) {
                    warnThrottled("Auth bridge async publication failed: " + safeMessage(throwable));
                }
            });
        } catch (Throwable throwable) {
            warnThrottled("Auth bridge could not schedule LibreLogin state event: " + safeMessage(throwable));
        }
    }

    void reconcileActiveSessions() {
        if (closed.get()) {
            return;
        }
        List<Player> onlinePlayers;
        try {
            onlinePlayers = List.copyOf(server.getAllPlayers());
        } catch (Throwable throwable) {
            warnThrottled("Auth bridge heartbeat could not list online players: " + safeMessage(throwable));
            return;
        }

        for (Player player : onlinePlayers) {
            try {
                if (closed.get()) {
                    return;
                }
                ServerConnection connection = player.getCurrentServer().orElse(null);
                if (connection == null || !sessions.hasActiveBinding(player, connection)) {
                    continue;
                }
                reconcile(player);
                sendState(connection, player);
            } catch (Throwable throwable) {
                warnThrottled("Auth bridge heartbeat failed for an online session: " + safeMessage(throwable));
            }
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
                publishStateChangeToCurrentServer(player);
            }, delayMillis);
        } catch (Throwable throwable) {
            warnThrottled("Auth bridge state synchronization could not be scheduled: " + safeMessage(throwable));
        }
    }

    private AuthState reconcile(Player player) {
        AuthState state = AuthState.PENDING;
        try {
            AuthState resolved = source.currentState(player);
            if (resolved != null) {
                state = resolved;
            }
        } catch (Throwable throwable) {
            warnThrottled("LibreLogin state lookup failed for " + player.getUsername() + ": " + safeMessage(throwable));
        }
        sessions.update(player, state);
        return state;
    }

    private void publishStateChangeToCurrentServer(Player player) {
        player.getCurrentServer().ifPresent(connection -> sendStateIfChanged(connection, player));
    }

    private void sendState(ServerConnection connection, Player player) {
        sendState(connection, player, false);
    }

    private void sendStateIfChanged(ServerConnection connection, Player player) {
        sendState(connection, player, true);
    }

    private void sendState(
            ServerConnection connection,
            Player player,
            boolean stateChangeOnly
    ) {
        String sourceServer = normalizeServerName(connection.getServerInfo().getName());
        if (!allowedServers.contains(sourceServer)) {
            return;
        }

        AuthSessionRegistry.Snapshot snapshot = stateChangeOnly
                ? sessions.nextIfStateChanged(player, connection)
                : sessions.next(player, connection);
        if (snapshot == null || snapshot.binding() == null) {
            return;
        }
        AuthSessionRegistry.Binding binding = snapshot.binding();
        String expectedBackendId = sourceBindings.get(sourceServer);
        if (!sourceServer.equals(binding.sourceServer())
                || !java.util.Objects.equals(expectedBackendId, binding.backendId())
                || binding.connection() != connection) {
            return;
        }

        try {
            long issuedAt = clock.millis();
            AuthFrame response = new AuthFrame(
                    ProtocolConstants.CURRENT_MAJOR,
                    ProtocolConstants.CURRENT_MINOR,
                    AuthMessageType.AUTH_STATE,
                    UUID.randomUUID(),
                    player.getUniqueId(),
                    binding.sessionId(),
                    snapshot.sequence(),
                    issuedAt,
                    saturatedAdd(issuedAt, messageTtlMillis),
                    randomBytes(ProtocolConstants.NONCE_BYTES),
                    binding.challenge(),
                    binding.backendId(),
                    snapshot.state(),
                    reason(snapshot.state()),
                    new byte[0]
            );

            byte[] payload = codec.encode(authenticator.sign(response));
            boolean sent = connection.sendPluginMessage(channel, payload);
            if (!sent) {
                warnThrottled("Auth bridge state could not be sent to '" + sourceServer + "'");
                return;
            }
            boolean transition = sessions.markPublished(player, snapshot);
            if (logTransitions && transition) {
                logTransition(player, sourceServer, snapshot.state());
            }
        } catch (Throwable throwable) {
            warnThrottled(
                    "Auth bridge state publication failed for '" + sourceServer + "': "
                            + safeMessage(throwable)
            );
        }
    }

    private void logTransition(Player player, String sourceServer, AuthState state) {
        try {
            transitionLogger.accept(
                    "Auth bridge: "
                            + player.getUsername()
                            + " -> "
                            + state
                            + " on "
                            + sourceServer
            );
        } catch (Throwable throwable) {
            warnThrottled("Auth bridge transition logging failed: " + safeMessage(throwable));
        }
    }

    private void validateChallengeSource(
            ServerConnection connection,
            String sourceServer,
            String expectedBackendId,
            AuthFrame challenge
    ) {
        if (challenge.messageType() != AuthMessageType.CHALLENGE) {
            throw new AuthProtocolException("Only CHALLENGE frames are accepted from backends");
        }
        if (challenge.authState() != AuthState.PENDING) {
            throw new AuthProtocolException("CHALLENGE state must be PENDING");
        }
        if (challenge.sequence() != 0L) {
            throw new AuthProtocolException("CHALLENGE sequence must be 0");
        }
        if (!challenge.reason().isEmpty()) {
            throw new AuthProtocolException("CHALLENGE reason must be empty");
        }
        if (!expectedBackendId.equals(challenge.backendId())) {
            throw new AuthProtocolException("Backend ID is not bound to source server '" + sourceServer + "'");
        }

        Player player = connection.getPlayer();
        if (!challenge.playerId().equals(player.getUniqueId())) {
            throw new AuthProtocolException("Player UUID does not match the server connection");
        }
        ExpectedServerConnection expected = expectedConnections.get(player.getUniqueId());
        if (expected != null && expected.expiresAtMillis() < clock.millis()) {
            expectedConnections.remove(player.getUniqueId(), expected);
            expected = null;
        }
        if (expected != null) {
            if (!expected.serverName().equals(sourceServer) || expected.previousConnection() == connection) {
                throw new AuthProtocolException("Challenge source is not the expected transfer target");
            }
            return;
        }
        if (player.getCurrentServer().orElse(null) != connection) {
            throw new AuthProtocolException("Challenge source is not the player's current backend connection");
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
        for (AuthSessionRegistry.Binding binding : sessions.clear()) {
            forget(binding);
        }
        replayWindow.clear();
        expectedConnections.clear();
        authenticator.close();
    }

    private void forgetIfReplaced(
            AuthSessionRegistry.Binding previous,
            AuthSessionRegistry.Binding current
    ) {
        if (previous == null) {
            return;
        }
        if (current == null
                || !previous.sessionId().equals(current.sessionId())
                || !previous.backendId().equals(current.backendId())) {
            forget(previous);
        }
    }

    private void forget(AuthSessionRegistry.Binding binding) {
        if (binding == null) {
            return;
        }
        AuthFrameVerifier verifier = verifiers.get(binding.backendId());
        if (verifier != null) {
            verifier.forgetSession(binding.playerId(), binding.sessionId());
        }
    }

    private byte[] randomBytes(int length) {
        byte[] value = new byte[length];
        secureRandom.nextBytes(value);
        return value;
    }

    private void warnThrottled(String message) {
        long now = clock.millis();
        long previous = lastWarningAt.get();
        if (now - previous < WARNING_INTERVAL_MILLIS || !lastWarningAt.compareAndSet(previous, now)) {
            return;
        }
        Message.warn(message);
    }

    private static Map<String, AuthFrameVerifier> createVerifiers(
            Map<String, String> sourceBindings,
            AuthSecurityPolicy policy,
            HmacSha256Authenticator authenticator,
            ReplayWindow replayWindow
    ) {
        Map<String, AuthFrameVerifier> created = new HashMap<>();
        for (String backendId : sourceBindings.values()) {
            created.put(
                    backendId,
                    new AuthFrameVerifier(backendId, policy, authenticator, replayWindow)
            );
        }
        return Map.copyOf(created);
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

    private static Map<String, String> normalizeSourceBindings(
            Map<String, String> configured,
            Set<String> allowedServers
    ) {
        Map<String, String> normalized = new HashMap<>();
        if (configured != null) {
            configured.forEach((server, backendId) -> {
                if (server == null || server.isBlank() || backendId == null || backendId.isBlank()) {
                    throw new IllegalStateException("Auth bridge source_bindings contains a blank key or value");
                }
                String source = normalizeServerName(server);
                String identity = backendId.trim();
                if (normalized.putIfAbsent(source, identity) != null) {
                    throw new IllegalStateException("Duplicate source binding for server '" + source + "'");
                }
            });
        }
        if (!normalized.keySet().equals(allowedServers)) {
            throw new IllegalStateException(
                    "Auth bridge source_bindings keys must exactly match allow_from server names"
            );
        }
        return Map.copyOf(normalized);
    }

    private static String normalizeServerName(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static long requireNonNegative(long value, String name) {
        if (value < 0L) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }

    private record ExpectedServerConnection(
            String serverName,
            ServerConnection previousConnection,
            long expiresAtMillis
    ) {
    }

    private static long requireHeartbeatInterval(
            Duration heartbeatInterval,
            Duration authorizationLease
    ) {
        java.util.Objects.requireNonNull(heartbeatInterval, "heartbeatInterval");
        java.util.Objects.requireNonNull(authorizationLease, "authorizationLease");
        long heartbeatMillis = heartbeatInterval.toMillis();
        long leaseMillis = authorizationLease.toMillis();
        if (leaseMillis < Duration.ofSeconds(5).toMillis()
                || leaseMillis > Duration.ofSeconds(300).toMillis()) {
            throw new IllegalArgumentException(
                    "authorizationLease must be between 5 and 300 seconds"
            );
        }
        if (heartbeatMillis <= 0L || saturatedMultiplyByTwo(heartbeatMillis) > leaseMillis) {
            throw new IllegalArgumentException(
                    "heartbeatInterval must be positive and fit at least twice inside "
                            + "authorizationLease"
            );
        }
        return heartbeatMillis;
    }

    private static long saturatedMultiplyByTwo(long value) {
        return value > Long.MAX_VALUE / 2L ? Long.MAX_VALUE : value * 2L;
    }

    private static long saturatedAdd(long value, long addend) {
        if (addend > 0L && value > Long.MAX_VALUE - addend) {
            return Long.MAX_VALUE;
        }
        return value + addend;
    }

    private static String reason(AuthState state) {
        return switch (state) {
            case PENDING -> "pending";
            case AWAITING_SECOND_FACTOR -> "awaiting_second_factor";
            case AUTHORIZED -> "authenticated";
            case REVOKED -> "revoked";
        };
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }
}
