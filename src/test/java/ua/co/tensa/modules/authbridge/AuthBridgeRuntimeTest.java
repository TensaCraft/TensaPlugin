package ua.co.tensa.modules.authbridge;

import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelRegistrar;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.junit.jupiter.api.Test;
import ua.co.tensa.authbridge.protocol.AuthFrame;
import ua.co.tensa.authbridge.protocol.AuthMessageType;
import ua.co.tensa.authbridge.protocol.AuthProtocolCodec;
import ua.co.tensa.authbridge.protocol.AuthState;
import ua.co.tensa.authbridge.protocol.ProtocolConstants;
import ua.co.tensa.authbridge.protocol.security.AuthSecurityPolicy;
import ua.co.tensa.authbridge.protocol.security.HmacSha256Authenticator;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static ua.co.tensa.modules.authbridge.AuthBridgeTestProxies.defaultValue;
import static ua.co.tensa.modules.authbridge.AuthBridgeTestProxies.proxy;

class AuthBridgeRuntimeTest {
    private static final Instant NOW = Instant.parse("2027-01-15T08:00:00Z");
    private static final String SOURCE_SERVER = "aero";
    private static final String BACKEND_ID = "aero-backend";
    private static final byte[] SECRET =
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    private final AuthProtocolCodec codec = new AuthProtocolCodec();

    @Test
    void acceptsBackendChallengeAndReturnsCanonicalBoundAuthState() {
        Harness harness = harness(SOURCE_SERVER);
        harness.source.state = AuthState.AUTHORIZED;
        harness.runtime.start();
        harness.scheduler.runAll();
        assertThat(harness.payloads).isEmpty();

        AuthFrame challenge = challenge(
                harness.playerId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                BACKEND_ID,
                bytes(ProtocolConstants.NONCE_BYTES, 10),
                bytes(ProtocolConstants.CHALLENGE_BYTES, 40)
        );
        byte[] payload = signAndEncode(challenge);
        PluginMessageEvent event = pluginMessage(harness.connection, harness.player, payload);

        harness.runtime.onPluginMessage(event);

        assertThat(event.getResult().isAllowed()).isFalse();
        assertThat(harness.payloads).hasSize(1);
        AuthFrame response = codec.decode(harness.payloads.getLast());
        assertThat(response.messageType()).isEqualTo(AuthMessageType.AUTH_STATE);
        assertThat(response.authState()).isEqualTo(AuthState.AUTHORIZED);
        assertThat(response.playerId()).isEqualTo(challenge.playerId());
        assertThat(response.sessionId()).isEqualTo(challenge.sessionId());
        assertThat(response.challenge()).isEqualTo(challenge.challenge());
        assertThat(response.backendId()).isEqualTo(challenge.backendId());
        assertThat(response.sequence()).isEqualTo(1L);
        assertThat(response.nonce()).hasSize(ProtocolConstants.NONCE_BYTES);
        try (var authenticator = new HmacSha256Authenticator(SECRET, codec)) {
            assertThat(authenticator.verify(response)).isTrue();
        }

        harness.runtime.onPluginMessage(pluginMessage(harness.connection, harness.player, payload));
        assertThat(harness.payloads).hasSize(1);

        AuthFrame retriedChallenge = challenge(
                harness.playerId,
                UUID.randomUUID(),
                challenge.sessionId(),
                BACKEND_ID,
                bytes(ProtocolConstants.NONCE_BYTES, 12),
                challenge.challenge()
        );
        harness.runtime.onPluginMessage(pluginMessage(
                harness.connection,
                harness.player,
                signAndEncode(retriedChallenge)
        ));
        AuthFrame retriedResponse = codec.decode(harness.payloads.getLast());
        assertThat(harness.payloads).hasSize(2);
        assertThat(retriedResponse.sessionId()).isEqualTo(challenge.sessionId());
        assertThat(retriedResponse.sequence()).isEqualTo(2L);

        AuthFrame restartedBackendChallenge = challenge(
                harness.playerId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                BACKEND_ID,
                bytes(ProtocolConstants.NONCE_BYTES, 11),
                bytes(ProtocolConstants.CHALLENGE_BYTES, 41)
        );
        harness.runtime.onPluginMessage(pluginMessage(
                harness.connection,
                harness.player,
                signAndEncode(restartedBackendChallenge)
        ));
        AuthFrame restartedBackendResponse = codec.decode(harness.payloads.getLast());
        assertThat(harness.payloads).hasSize(3);
        assertThat(restartedBackendResponse.sessionId())
                .isEqualTo(restartedBackendChallenge.sessionId());
        assertThat(restartedBackendResponse.sequence()).isEqualTo(1L);
        harness.runtime.close();
    }

    @Test
    void unchangedReconciliationsDoNotDuplicateFramesButPostConnectStillResends() {
        Harness harness = harness(SOURCE_SERVER);
        harness.runtime.start();
        harness.scheduler.runAll();
        AuthFrame challenge = challenge(
                harness.playerId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                BACKEND_ID,
                bytes(ProtocolConstants.NONCE_BYTES, 20),
                bytes(ProtocolConstants.CHALLENGE_BYTES, 50)
        );
        harness.runtime.onPluginMessage(pluginMessage(
                harness.connection,
                harness.player,
                signAndEncode(challenge)
        ));
        assertThat(codec.decode(harness.payloads.getLast()).authState()).isEqualTo(AuthState.PENDING);

        harness.runtime.synchronizeAfterLogin(harness.player);
        harness.scheduler.runAll();
        assertThat(harness.payloads).hasSize(1);

        harness.source.state = AuthState.AUTHORIZED;
        harness.source.fireStateChange(harness.player);
        assertThat(harness.payloads).hasSize(1);
        harness.scheduler.runAll();
        AuthFrame authenticated = codec.decode(harness.payloads.getLast());
        assertThat(authenticated.authState()).isEqualTo(AuthState.AUTHORIZED);
        assertThat(authenticated.sequence()).isEqualTo(2L);

        harness.source.fireStateChange(harness.player);
        harness.scheduler.runAll();
        assertThat(harness.payloads).hasSize(2);

        harness.runtime.resendToCurrentServer(harness.player);
        AuthFrame resent = codec.decode(harness.payloads.getLast());
        assertThat(resent.sequence()).isEqualTo(3L);
        assertThat(resent.sessionId()).isEqualTo(challenge.sessionId());
        assertThat(resent.challenge()).isEqualTo(challenge.challenge());
        harness.runtime.close();
    }

    @Test
    void heartbeatReconcilesOnlyActiveChallengesAndFailsClosedWithMonotonicSequence() {
        Harness harness = harness(SOURCE_SERVER);
        harness.runtime.start();
        harness.scheduler.runAll();
        assertThat(harness.scheduler.recurringIntervalMillis).isEqualTo(10_000L);

        int lookupsBeforeUnboundHeartbeat = harness.source.lookups;
        harness.scheduler.runHeartbeat();
        assertThat(harness.source.lookups).isEqualTo(lookupsBeforeUnboundHeartbeat);
        assertThat(harness.payloads).isEmpty();

        AuthFrame challenge = challenge(
                harness.playerId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                BACKEND_ID,
                bytes(ProtocolConstants.NONCE_BYTES, 25),
                bytes(ProtocolConstants.CHALLENGE_BYTES, 55)
        );
        harness.runtime.onPluginMessage(pluginMessage(
                harness.connection,
                harness.player,
                signAndEncode(challenge)
        ));
        assertThat(codec.decode(harness.payloads.getLast()).sequence()).isEqualTo(1L);

        harness.scheduler.runHeartbeat();
        AuthFrame unchangedHeartbeat = codec.decode(harness.payloads.getLast());
        assertThat(unchangedHeartbeat.authState()).isEqualTo(AuthState.PENDING);
        assertThat(unchangedHeartbeat.sequence()).isEqualTo(2L);

        harness.source.state = AuthState.AWAITING_SECOND_FACTOR;
        harness.scheduler.runHeartbeat();
        AuthFrame awaitingSecondFactor = codec.decode(harness.payloads.getLast());
        assertThat(awaitingSecondFactor.authState()).isEqualTo(AuthState.AWAITING_SECOND_FACTOR);
        assertThat(awaitingSecondFactor.sequence()).isEqualTo(3L);

        harness.source.lookupFailure = new IllegalStateException("LibreLogin unavailable");
        harness.scheduler.runHeartbeat();
        AuthFrame failedClosed = codec.decode(harness.payloads.getLast());
        assertThat(failedClosed.authState()).isEqualTo(AuthState.PENDING);
        assertThat(failedClosed.sequence()).isEqualTo(4L);
        assertThat(failedClosed.sessionId()).isEqualTo(challenge.sessionId());
        assertThat(failedClosed.challenge()).isEqualTo(challenge.challenge());
        harness.runtime.close();
    }

    @Test
    void logsOnlySuccessfulStateTransitionsWhileHeartbeatAndResyncFramesContinue() {
        Harness harness = harness(SOURCE_SERVER, true);
        harness.source.state = AuthState.AUTHORIZED;
        harness.runtime.start();
        harness.scheduler.runAll();
        AuthFrame challenge = challenge(
                harness.playerId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                BACKEND_ID,
                bytes(ProtocolConstants.NONCE_BYTES, 27),
                bytes(ProtocolConstants.CHALLENGE_BYTES, 57)
        );

        harness.runtime.onPluginMessage(pluginMessage(
                harness.connection,
                harness.player,
                signAndEncode(challenge)
        ));
        harness.scheduler.runHeartbeat();

        assertThat(harness.payloads).hasSize(2);
        assertThat(codec.decode(harness.payloads.getLast()).sequence()).isEqualTo(2L);
        assertThat(harness.transitionLogs)
                .singleElement()
                .asString()
                .contains("AUTHORIZED");

        harness.source.state = AuthState.AWAITING_SECOND_FACTOR;
        harness.source.fireStateChange(harness.player);
        harness.scheduler.runAll();
        harness.runtime.resendToCurrentServer(harness.player);

        assertThat(harness.payloads).hasSize(4);
        assertThat(codec.decode(harness.payloads.getLast()).sequence()).isEqualTo(4L);
        assertThat(harness.transitionLogs).hasSize(2);
        assertThat(harness.transitionLogs.getLast()).contains("AWAITING_SECOND_FACTOR");
        harness.runtime.close();
    }

    @Test
    void authorizationTimingUsesConfiguredLeaseAndRequiresSafetyMargin() {
        assertThatCode(() -> LibreLoginAuthBridgeModule.validateAuthorizationTiming(10, 30))
                .doesNotThrowAnyException();
        assertThatCode(() -> LibreLoginAuthBridgeModule.validateAuthorizationTiming(15, 30))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> LibreLoginAuthBridgeModule.validateAuthorizationTiming(16, 30))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least twice");
        assertThatThrownBy(() -> LibreLoginAuthBridgeModule.validateAuthorizationTiming(0, 30))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("greater than 0");
        assertThatThrownBy(() -> LibreLoginAuthBridgeModule.validateAuthorizationTiming(2, 4))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("between 5 and 300");
        assertThatThrownBy(() -> LibreLoginAuthBridgeModule.validateAuthorizationTiming(10, 301))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("between 5 and 300");

        assertThatThrownBy(() -> harness(
                SOURCE_SERVER,
                Duration.ofSeconds(16),
                Duration.ofSeconds(30)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least twice");
    }

    @Test
    void rejectsSpoofedBackendIdentityAndStaleTransportSourceButAlwaysHandlesChannel() {
        Harness harness = harness(SOURCE_SERVER);
        harness.runtime.start();
        harness.scheduler.runAll();

        AuthFrame spoofed = challenge(
                harness.playerId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "other-backend",
                bytes(ProtocolConstants.NONCE_BYTES, 30),
                bytes(ProtocolConstants.CHALLENGE_BYTES, 60)
        );
        PluginMessageEvent spoofedEvent = pluginMessage(
                harness.connection,
                harness.player,
                signAndEncode(spoofed)
        );
        harness.runtime.onPluginMessage(spoofedEvent);

        AtomicReference<ServerConnection> staleCurrent = new AtomicReference<>();
        Player stalePlayer = player(harness.playerId, staleCurrent);
        ServerConnection staleConnection = connection(SOURCE_SERVER, stalePlayer, harness.payloads);
        staleCurrent.set(connection("other", stalePlayer, harness.payloads));
        AuthFrame stale = challenge(
                harness.playerId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                BACKEND_ID,
                bytes(ProtocolConstants.NONCE_BYTES, 40),
                bytes(ProtocolConstants.CHALLENGE_BYTES, 70)
        );
        PluginMessageEvent staleEvent = pluginMessage(staleConnection, stalePlayer, signAndEncode(stale));
        harness.runtime.onPluginMessage(staleEvent);

        assertThat(spoofedEvent.getResult().isAllowed()).isFalse();
        assertThat(staleEvent.getResult().isAllowed()).isFalse();
        assertThat(harness.payloads).isEmpty();
        harness.runtime.close();
    }

    @Test
    void acceptsRegisteredServerAliasesForOneBackendAndRejectsUntrustedSourcesAndIdentities() {
        List<String> allowedServers = List.of("aero-auth", "aeronautics");
        Map<String, String> sourceBindings = Map.of(
                "aero-auth", "aero",
                "aeronautics", "aero"
        );

        Harness authAlias = harness("aero-auth", allowedServers, sourceBindings);
        PluginMessageEvent authAliasEvent = sendChallenge(authAlias, "aero", 80);

        Harness aeronauticsAlias = harness("aeronautics", allowedServers, sourceBindings);
        PluginMessageEvent aeronauticsAliasEvent = sendChallenge(aeronauticsAlias, "aero", 90);

        Harness untrustedSource = harness("other", allowedServers, sourceBindings);
        PluginMessageEvent untrustedSourceEvent = sendChallenge(untrustedSource, "aero", 100);

        Harness wrongBackend = harness("aeronautics", allowedServers, sourceBindings);
        PluginMessageEvent wrongBackendEvent = sendChallenge(wrongBackend, "other-backend", 110);

        assertThat(authAliasEvent.getResult().isAllowed()).isFalse();
        assertThat(aeronauticsAliasEvent.getResult().isAllowed()).isFalse();
        assertThat(untrustedSourceEvent.getResult().isAllowed()).isFalse();
        assertThat(wrongBackendEvent.getResult().isAllowed()).isFalse();
        assertThat(authAlias.payloads).hasSize(1);
        assertThat(aeronauticsAlias.payloads).hasSize(1);
        assertThat(codec.decode(authAlias.payloads.getLast()).backendId()).isEqualTo("aero");
        assertThat(codec.decode(aeronauticsAlias.payloads.getLast()).backendId()).isEqualTo("aero");
        assertThat(untrustedSource.payloads).isEmpty();
        assertThat(wrongBackend.payloads).isEmpty();

        authAlias.runtime.close();
        aeronauticsAlias.runtime.close();
        untrustedSource.runtime.close();
        wrongBackend.runtime.close();
    }

    @Test
    void transfersOnePlayerBetweenAliasesWithoutKeepingTheOldFrozenBinding() {
        List<String> allowedServers = List.of("aero-auth", "aeronautics");
        Map<String, String> sourceBindings = Map.of(
                "aero-auth", "aero",
                "aeronautics", "aero"
        );
        Harness harness = harness("aero-auth", allowedServers, sourceBindings);
        harness.runtime.start();
        harness.scheduler.runAll();

        AuthFrame authChallenge = challenge(
                harness.playerId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "aero",
                bytes(ProtocolConstants.NONCE_BYTES, 120),
                bytes(ProtocolConstants.CHALLENGE_BYTES, 150)
        );
        harness.runtime.onPluginMessage(pluginMessage(
                harness.connection,
                harness.player,
                signAndEncode(authChallenge)
        ));
        assertThat(codec.decode(harness.payloads.getLast()).authState())
                .isEqualTo(AuthState.PENDING);

        List<byte[]> gameplayPayloads = new ArrayList<>();
        ServerConnection gameplay = connection("aeronautics", harness.player, gameplayPayloads);
        harness.currentConnection.set(gameplay);
        harness.source.state = AuthState.AUTHORIZED;
        AuthFrame gameplayChallenge = challenge(
                harness.playerId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "aero",
                bytes(ProtocolConstants.NONCE_BYTES, 121),
                bytes(ProtocolConstants.CHALLENGE_BYTES, 151)
        );

        harness.runtime.onPluginMessage(pluginMessage(
                gameplay,
                harness.player,
                signAndEncode(gameplayChallenge)
        ));
        AuthFrame transferred = codec.decode(gameplayPayloads.getLast());
        assertThat(transferred.authState()).isEqualTo(AuthState.AUTHORIZED);
        assertThat(transferred.sessionId()).isEqualTo(gameplayChallenge.sessionId());
        assertThat(transferred.sequence()).isEqualTo(1L);

        harness.runtime.onPluginMessage(pluginMessage(
                harness.connection,
                harness.player,
                signAndEncode(challenge(
                        harness.playerId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "aero",
                        bytes(ProtocolConstants.NONCE_BYTES, 122),
                        bytes(ProtocolConstants.CHALLENGE_BYTES, 152)
                ))
        ));
        harness.scheduler.runHeartbeat();

        assertThat(harness.payloads).hasSize(1);
        assertThat(gameplayPayloads).hasSize(2);
        AuthFrame heartbeat = codec.decode(gameplayPayloads.getLast());
        assertThat(heartbeat.sessionId()).isEqualTo(gameplayChallenge.sessionId());
        assertThat(heartbeat.sequence()).isEqualTo(2L);
        harness.runtime.close();
    }

    @Test
    void acceptsChallengeFromExpectedTransferTargetBeforeVelocitySwitchesCurrentConnection() {
        List<String> allowedServers = List.of("aero-auth", "aeronautics");
        Map<String, String> sourceBindings = Map.of(
                "aero-auth", "aero",
                "aeronautics", "aero"
        );
        Harness harness = harness("aero-auth", allowedServers, sourceBindings);
        harness.runtime.start();
        harness.scheduler.runAll();
        List<byte[]> gameplayPayloads = new ArrayList<>();
        ServerConnection gameplay = connection("aeronautics", harness.player, gameplayPayloads);
        harness.source.state = AuthState.AUTHORIZED;
        RegisteredServer authServer = registeredServer("aero-auth");
        RegisteredServer gameplayServer = registeredServer("aeronautics");
        harness.runtime.onServerPreConnect(new ServerPreConnectEvent(
                harness.player, gameplayServer, authServer
        ));

        harness.runtime.onPluginMessage(pluginMessage(
                harness.connection,
                harness.player,
                signAndEncode(challenge(
                        harness.playerId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "aero",
                        bytes(ProtocolConstants.NONCE_BYTES, 122),
                        bytes(ProtocolConstants.CHALLENGE_BYTES, 152)
                ))
        ));
        assertThat(harness.payloads).isEmpty();

        harness.runtime.onPluginMessage(pluginMessage(
                gameplay,
                harness.player,
                signAndEncode(challenge(
                        harness.playerId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "aero",
                        bytes(ProtocolConstants.NONCE_BYTES, 123),
                        bytes(ProtocolConstants.CHALLENGE_BYTES, 153)
                ))
        ));

        assertThat(gameplayPayloads).hasSize(1);
        assertThat(codec.decode(gameplayPayloads.getFirst()).authState()).isEqualTo(AuthState.AUTHORIZED);
        assertThat(harness.currentConnection.get()).isSameAs(harness.connection);

        harness.currentConnection.set(gameplay);
        harness.runtime.onServerPostConnect(new ServerPostConnectEvent(harness.player, authServer));
        harness.runtime.onPluginMessage(pluginMessage(
                harness.connection,
                harness.player,
                signAndEncode(challenge(
                        harness.playerId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "aero",
                        bytes(ProtocolConstants.NONCE_BYTES, 124),
                        bytes(ProtocolConstants.CHALLENGE_BYTES, 154)
                ))
        ));
        assertThat(harness.payloads).isEmpty();
        harness.runtime.close();
    }

    @Test
    void disallowedBackendCannotQueryOrReceiveStateAndCloseCleansUp() {
        Harness harness = harness("other");
        harness.runtime.beginSession(harness.player);
        harness.runtime.resendToCurrentServer(harness.player);
        PluginMessageEvent event = pluginMessage(
                harness.connection,
                harness.player,
                new byte[]{1, 2, 3}
        );
        harness.runtime.onPluginMessage(event);
        harness.runtime.close();

        assertThat(event.getResult().isAllowed()).isFalse();
        assertThat(harness.payloads).isEmpty();
        assertThat(harness.source.closed).isTrue();
        assertThat(harness.channels.unregisterCount).hasValue(1);
    }

    private Harness harness(String connectionServer) {
        return harness(
                connectionServer,
                Duration.ofSeconds(10),
                Duration.ofSeconds(30)
        );
    }

    private Harness harness(String connectionServer, boolean logTransitions) {
        return harness(
                connectionServer,
                List.of(SOURCE_SERVER),
                Map.of(SOURCE_SERVER, BACKEND_ID),
                Duration.ofSeconds(10),
                Duration.ofSeconds(30),
                logTransitions
        );
    }

    private Harness harness(
            String connectionServer,
            Duration heartbeatInterval,
            Duration authorizationLease
    ) {
        return harness(
                connectionServer,
                List.of(SOURCE_SERVER),
                Map.of(SOURCE_SERVER, BACKEND_ID),
                heartbeatInterval,
                authorizationLease
        );
    }

    private Harness harness(
            String connectionServer,
            List<String> allowedServers,
            Map<String, String> sourceBindings
    ) {
        return harness(
                connectionServer,
                allowedServers,
                sourceBindings,
                Duration.ofSeconds(10),
                Duration.ofSeconds(30)
        );
    }

    private Harness harness(
            String connectionServer,
            List<String> allowedServers,
            Map<String, String> sourceBindings,
            Duration heartbeatInterval,
            Duration authorizationLease
    ) {
        return harness(
                connectionServer,
                allowedServers,
                sourceBindings,
                heartbeatInterval,
                authorizationLease,
                false
        );
    }

    private Harness harness(
            String connectionServer,
            List<String> allowedServers,
            Map<String, String> sourceBindings,
            Duration heartbeatInterval,
            Duration authorizationLease,
            boolean logTransitions
    ) {
        UUID playerId = UUID.randomUUID();
        List<byte[]> payloads = new ArrayList<>();
        List<String> transitionLogs = new ArrayList<>();
        AtomicReference<ServerConnection> connectionRef = new AtomicReference<>();
        Player player = player(playerId, connectionRef);
        ServerConnection connection = connection(connectionServer, player, payloads);
        connectionRef.set(connection);
        RecordingChannelRegistrar channels = new RecordingChannelRegistrar();
        FakeAuthenticationSource source = new FakeAuthenticationSource();
        RecordingScheduler scheduler = new RecordingScheduler();
        AuthBridgeRuntime runtime = new AuthBridgeRuntime(
                proxyServer(player, channels.proxy()),
                source,
                scheduler,
                allowedServers,
                sourceBindings,
                SECRET,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new SecureRandom(),
                new AuthSecurityPolicy(Duration.ofSeconds(5), Duration.ofSeconds(15), 128),
                50L,
                heartbeatInterval,
                authorizationLease,
                logTransitions,
                transitionLogs::add
        );
        return new Harness(
                playerId,
                player,
                connection,
                connectionRef,
                payloads,
                transitionLogs,
                channels,
                source,
                scheduler,
                runtime
        );
    }

    private PluginMessageEvent sendChallenge(Harness harness, String backendId, int seed) {
        harness.runtime.start();
        harness.scheduler.runAll();
        AuthFrame challenge = challenge(
                harness.playerId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                backendId,
                bytes(ProtocolConstants.NONCE_BYTES, seed),
                bytes(ProtocolConstants.CHALLENGE_BYTES, seed + ProtocolConstants.NONCE_BYTES)
        );
        PluginMessageEvent event = pluginMessage(
                harness.connection,
                harness.player,
                signAndEncode(challenge)
        );
        harness.runtime.onPluginMessage(event);
        return event;
    }

    private AuthFrame challenge(
            UUID playerId,
            UUID messageId,
            UUID sessionId,
            String backendId,
            byte[] nonce,
            byte[] challenge
    ) {
        return new AuthFrame(
                ProtocolConstants.CURRENT_MAJOR,
                ProtocolConstants.CURRENT_MINOR,
                AuthMessageType.CHALLENGE,
                messageId,
                playerId,
                sessionId,
                0L,
                NOW.toEpochMilli(),
                NOW.plusSeconds(15).toEpochMilli(),
                nonce,
                challenge,
                backendId,
                AuthState.PENDING,
                "",
                new byte[0]
        );
    }

    private byte[] signAndEncode(AuthFrame frame) {
        try (var authenticator = new HmacSha256Authenticator(SECRET, codec)) {
            return codec.encode(authenticator.sign(frame));
        }
    }

    private PluginMessageEvent pluginMessage(
            ServerConnection connection,
            Player player,
            byte[] payload
    ) {
        return new PluginMessageEvent(
                connection,
                player,
                MinecraftChannelIdentifier.from(ProtocolConstants.CHANNEL),
                payload
        );
    }

    private ProxyServer proxyServer(Player player, ChannelRegistrar registrar) {
        return proxy(ProxyServer.class, (method, args) -> switch (method.getName()) {
            case "getAllPlayers" -> List.of(player);
            case "getChannelRegistrar" -> registrar;
            default -> defaultValue(method.getReturnType());
        });
    }

    private Player player(UUID uuid, AtomicReference<ServerConnection> connection) {
        return proxy(Player.class, (method, args) -> switch (method.getName()) {
            case "getUniqueId" -> uuid;
            case "getUsername" -> "Steve";
            case "getCurrentServer" -> Optional.ofNullable(connection.get());
            default -> defaultValue(method.getReturnType());
        });
    }

    private ServerConnection connection(String name, Player player, List<byte[]> payloads) {
        ServerInfo info = new ServerInfo(name, new InetSocketAddress("127.0.0.1", 25565));
        return proxy(ServerConnection.class, (method, args) -> switch (method.getName()) {
            case "getServerInfo" -> info;
            case "getPlayer" -> player;
            case "sendPluginMessage" -> {
                if (args.length > 1 && args[1] instanceof byte[] payload) {
                    payloads.add(payload.clone());
                }
                yield true;
            }
            default -> defaultValue(method.getReturnType());
        });
    }

    private RegisteredServer registeredServer(String name) {
        ServerInfo info = new ServerInfo(name, new InetSocketAddress("127.0.0.1", 25565));
        return proxy(RegisteredServer.class, (method, args) -> switch (method.getName()) {
            case "getServerInfo" -> info;
            default -> defaultValue(method.getReturnType());
        });
    }

    private byte[] bytes(int length, int seed) {
        byte[] value = new byte[length];
        for (int index = 0; index < length; index++) {
            value[index] = (byte) (seed + index);
        }
        return value;
    }

    private record Harness(
            UUID playerId,
            Player player,
            ServerConnection connection,
            AtomicReference<ServerConnection> currentConnection,
            List<byte[]> payloads,
            List<String> transitionLogs,
            RecordingChannelRegistrar channels,
            FakeAuthenticationSource source,
            RecordingScheduler scheduler,
            AuthBridgeRuntime runtime
    ) {
    }

    private static final class FakeAuthenticationSource implements AuthenticationStateSource {
        private AuthState state = AuthState.PENDING;
        private Consumer<Player> listener;
        private boolean closed;
        private int lookups;
        private RuntimeException lookupFailure;

        @Override
        public AuthState currentState(Player player) {
            lookups++;
            if (lookupFailure != null) {
                throw lookupFailure;
            }
            return state;
        }

        @Override
        public void subscribeStateChanges(Consumer<Player> listener) {
            this.listener = listener;
        }

        private void fireStateChange(Player player) {
            listener.accept(player);
        }

        @Override
        public void close() {
            closed = true;
            listener = null;
        }
    }

    private static final class RecordingScheduler implements BridgeScheduler {
        private final Queue<Runnable> tasks = new ArrayDeque<>();
        private Runnable recurringTask;
        private long recurringIntervalMillis;

        @Override
        public void execute(Runnable task) {
            tasks.add(task);
        }

        @Override
        public void delayed(Runnable task, long delayMillis) {
            tasks.add(task);
        }

        @Override
        public void repeating(Runnable task, long initialDelayMillis, long intervalMillis) {
            recurringTask = task;
            recurringIntervalMillis = intervalMillis;
        }

        private void runAll() {
            while (!tasks.isEmpty()) {
                tasks.remove().run();
            }
        }

        private void runHeartbeat() {
            if (recurringTask != null) {
                recurringTask.run();
            }
        }
    }

    private static final class RecordingChannelRegistrar {
        private final AtomicInteger unregisterCount = new AtomicInteger();

        private ChannelRegistrar proxy() {
            return AuthBridgeTestProxies.proxy(ChannelRegistrar.class, (method, args) -> {
                if ("unregister".equals(method.getName())) {
                    unregisterCount.incrementAndGet();
                }
                return defaultValue(method.getReturnType());
            });
        }
    }
}
