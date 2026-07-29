package ua.co.tensa.modules.authbridge;

import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.ChannelRegistrar;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.ServerInfo;
import org.junit.jupiter.api.Test;
import ua.co.tensa.authbridge.protocol.AuthBridgeMessage;
import ua.co.tensa.authbridge.protocol.AuthBridgeMessageType;
import ua.co.tensa.authbridge.protocol.AuthBridgeProtocol;
import ua.co.tensa.authbridge.protocol.AuthBridgeState;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.co.tensa.modules.authbridge.AuthBridgeTestProxies.defaultValue;
import static ua.co.tensa.modules.authbridge.AuthBridgeTestProxies.proxy;

class AuthBridgeRuntimeTest {
    private static final Instant NOW = Instant.parse("2026-07-29T12:00:00Z");
    private static final byte[] SECRET =
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    @Test
    void startsLockedPublishesAsyncAuthAndResendsOnBackendQuery() throws Exception {
        UUID playerId = UUID.randomUUID();
        List<byte[]> payloads = new ArrayList<>();
        AtomicReference<ServerConnection> connectionRef = new AtomicReference<>();
        Player player = player(playerId, connectionRef);
        ServerConnection connection = connection("aero", player, payloads);
        connectionRef.set(connection);
        RecordingChannelRegistrar channels = new RecordingChannelRegistrar();
        ProxyServer proxyServer = proxyServer(player, channels.proxy());
        FakeAuthenticationSource source = new FakeAuthenticationSource();
        RecordingScheduler scheduler = new RecordingScheduler();
        AuthBridgeRuntime runtime = runtime(proxyServer, source, scheduler);

        runtime.start();
        scheduler.runAll();

        AuthBridgeMessage initial = decode(payloads.getLast());
        assertThat(initial.state()).isEqualTo(AuthBridgeState.LOCKED);
        assertThat(initial.backendChallenge()).isEmpty();

        source.state = AuthBridgeState.AUTHORIZED;
        source.fireAuthenticated(player);
        assertThat(payloads).hasSize(1);
        scheduler.runAll();

        AuthBridgeMessage authorized = decode(payloads.getLast());
        assertThat(authorized.state()).isEqualTo(AuthBridgeState.AUTHORIZED);
        assertThat(authorized.sessionId()).isEqualTo(initial.sessionId());

        AuthBridgeMessage query = new AuthBridgeMessage(
                AuthBridgeProtocol.VERSION,
                AuthBridgeMessageType.QUERY,
                playerId,
                authorized.sessionId(),
                "backend-challenge-01",
                1L,
                NOW.toEpochMilli(),
                NOW.plusSeconds(10).toEpochMilli(),
                AuthBridgeState.LOCKED
        );
        byte[] queryPayload = AuthBridgeProtocol.encode(query, SECRET);
        PluginMessageEvent event = new PluginMessageEvent(
                connection,
                player,
                MinecraftChannelIdentifier.from(AuthBridgeProtocol.CHANNEL),
                queryPayload
        );

        runtime.onPluginMessage(event);

        assertThat(event.getResult().isAllowed()).isFalse();
        AuthBridgeMessage response = decode(payloads.getLast());
        assertThat(response.type()).isEqualTo(AuthBridgeMessageType.STATE);
        assertThat(response.backendChallenge()).isEqualTo("backend-challenge-01");
        assertThat(response.state()).isEqualTo(AuthBridgeState.AUTHORIZED);

        int afterFirstQuery = payloads.size();
        runtime.onPluginMessage(new PluginMessageEvent(
                connection,
                player,
                MinecraftChannelIdentifier.from(AuthBridgeProtocol.CHANNEL),
                queryPayload
        ));
        assertThat(payloads).hasSize(afterFirstQuery);

        runtime.close();
        assertThat(source.closed).isTrue();
        assertThat(channels.unregisterCount).hasValue(1);
    }

    @Test
    void postLoginSyncIsDelayedAndDisconnectDoesNotReuseOldSession() throws Exception {
        UUID playerId = UUID.randomUUID();
        List<byte[]> payloads = new ArrayList<>();
        AtomicReference<ServerConnection> oldConnectionRef = new AtomicReference<>();
        Player oldPlayer = player(playerId, oldConnectionRef);
        ServerConnection oldConnection = connection("aero", oldPlayer, payloads);
        oldConnectionRef.set(oldConnection);
        RecordingScheduler scheduler = new RecordingScheduler();
        FakeAuthenticationSource source = new FakeAuthenticationSource();
        AuthBridgeRuntime runtime = runtime(
                proxyServer(oldPlayer, new RecordingChannelRegistrar().proxy()),
                source,
                scheduler
        );
        runtime.start();
        scheduler.runAll();
        UUID oldSession = decode(payloads.getLast()).sessionId();

        runtime.synchronizeAfterLogin(oldPlayer);
        assertThat(scheduler.queued()).isEqualTo(1);
        scheduler.runAll();

        AtomicReference<ServerConnection> newConnectionRef = new AtomicReference<>();
        Player newPlayer = player(playerId, newConnectionRef);
        ServerConnection newConnection = connection("aero", newPlayer, payloads);
        newConnectionRef.set(newConnection);
        runtime.beginSession(newPlayer);
        runtime.cleanupSession(oldPlayer);
        runtime.resendToCurrentServer(newPlayer);

        assertThat(decode(payloads.getLast()).sessionId()).isNotEqualTo(oldSession);
        runtime.close();
    }

    @Test
    void serverPostConnectResendsLockedStateWithoutEarlyLibreLoginReconcile() throws Exception {
        UUID playerId = UUID.randomUUID();
        List<byte[]> payloads = new ArrayList<>();
        AtomicReference<ServerConnection> connectionRef = new AtomicReference<>();
        Player player = player(playerId, connectionRef);
        connectionRef.set(connection("aero", player, payloads));
        FakeAuthenticationSource source = new FakeAuthenticationSource();
        source.state = AuthBridgeState.AUTHORIZED;
        RecordingScheduler scheduler = new RecordingScheduler();
        AuthBridgeRuntime runtime = runtime(
                proxyServer(player, new RecordingChannelRegistrar().proxy()),
                source,
                scheduler
        );

        runtime.beginSession(player);
        runtime.resendToCurrentServer(player);

        assertThat(decode(payloads.getLast()).state()).isEqualTo(AuthBridgeState.LOCKED);
        runtime.close();
    }

    @Test
    void handlesChannelImmediatelyAndDoesNotPublishToDisallowedBackend() {
        UUID playerId = UUID.randomUUID();
        List<byte[]> payloads = new ArrayList<>();
        AtomicReference<ServerConnection> connectionRef = new AtomicReference<>();
        Player player = player(playerId, connectionRef);
        ServerConnection connection = connection("other", player, payloads);
        connectionRef.set(connection);
        AuthBridgeRuntime runtime = runtime(
                proxyServer(player, new RecordingChannelRegistrar().proxy()),
                new FakeAuthenticationSource(),
                new RecordingScheduler()
        );

        runtime.beginSession(player);
        runtime.resendToCurrentServer(player);
        PluginMessageEvent event = new PluginMessageEvent(
                connection,
                player,
                MinecraftChannelIdentifier.from(AuthBridgeProtocol.CHANNEL),
                new byte[]{1, 2, 3}
        );
        runtime.onPluginMessage(event);

        assertThat(event.getResult().isAllowed()).isFalse();
        assertThat(payloads).isEmpty();
        runtime.close();
    }

    private AuthBridgeRuntime runtime(
            ProxyServer server,
            FakeAuthenticationSource source,
            RecordingScheduler scheduler
    ) {
        return new AuthBridgeRuntime(
                server,
                source,
                scheduler,
                List.of("aero"),
                SECRET,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofSeconds(10),
                Duration.ofSeconds(2),
                50L,
                false
        );
    }

    private AuthBridgeMessage decode(byte[] payload) throws Exception {
        return AuthBridgeProtocol.decodeAndVerify(payload, SECRET);
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

    private static final class FakeAuthenticationSource implements AuthenticationStateSource {
        private AuthBridgeState state = AuthBridgeState.LOCKED;
        private Consumer<Player> listener;
        private boolean closed;

        @Override
        public AuthBridgeState currentState(Player player) {
            return state;
        }

        @Override
        public void subscribeAuthenticated(Consumer<Player> listener) {
            this.listener = listener;
        }

        private void fireAuthenticated(Player player) {
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

        @Override
        public void execute(Runnable task) {
            tasks.add(task);
        }

        @Override
        public void delayed(Runnable task, long delayMillis) {
            tasks.add(task);
        }

        private int queued() {
            return tasks.size();
        }

        private void runAll() {
            while (!tasks.isEmpty()) {
                tasks.remove().run();
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
