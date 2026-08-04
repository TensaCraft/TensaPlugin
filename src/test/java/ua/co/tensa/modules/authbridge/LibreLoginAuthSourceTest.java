package ua.co.tensa.modules.authbridge;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import org.junit.jupiter.api.Test;
import ua.co.tensa.authbridge.protocol.AuthState;
import xyz.kyngs.librelogin.api.LibreLoginPlugin;
import xyz.kyngs.librelogin.api.authorization.AuthorizationProvider;
import xyz.kyngs.librelogin.api.event.Event;
import xyz.kyngs.librelogin.api.event.EventProvider;
import xyz.kyngs.librelogin.api.event.EventType;
import xyz.kyngs.librelogin.api.event.EventTypes;
import xyz.kyngs.librelogin.api.event.events.AuthenticatedEvent;
import xyz.kyngs.librelogin.api.event.events.LimboServerChooseEvent;
import xyz.kyngs.librelogin.api.event.events.LobbyServerChooseEvent;
import xyz.kyngs.librelogin.api.event.events.PremiumLoginSwitchEvent;
import xyz.kyngs.librelogin.api.event.events.WrongPasswordEvent;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static ua.co.tensa.modules.authbridge.AuthBridgeTestProxies.defaultValue;
import static ua.co.tensa.modules.authbridge.AuthBridgeTestProxies.proxy;

class LibreLoginAuthSourceTest {
    @Test
    void mapsLibreLoginStateAndUnsubscribesTheExactConsumer() {
        AtomicBoolean authorized = new AtomicBoolean();
        AtomicBoolean awaitingTwoFactor = new AtomicBoolean();
        AuthorizationProvider<Player> authorization = authorization(authorized, awaitingTwoFactor);
        FakeEventProvider events = new FakeEventProvider();
        LibreLoginAuthSource source = new LibreLoginAuthSource(plugin(authorization, events));
        Player player = player();
        AtomicInteger callbacks = new AtomicInteger();

        source.subscribeStateChanges(ignored -> callbacks.incrementAndGet());
        assertThat(source.currentState(player)).isEqualTo(AuthState.PENDING);

        authorized.set(true);
        assertThat(source.currentState(player)).isEqualTo(AuthState.AUTHORIZED);

        awaitingTwoFactor.set(true);
        assertThat(source.currentState(player)).isEqualTo(AuthState.AWAITING_SECOND_FACTOR);

        events.fireAuthenticated(player);
        events.fireWrongPassword(player);
        events.firePremiumLoginSwitch(player);
        assertThat(callbacks).hasValue(3);

        source.close();
        assertThat(events.unsubscribedExactConsumers).hasValue(3);
    }

    @Test
    void consumerFailureNeverEscapesIntoLibreLogin() {
        FakeEventProvider events = new FakeEventProvider();
        LibreLoginAuthSource source = new LibreLoginAuthSource(
                plugin(authorization(new AtomicBoolean(true), new AtomicBoolean()), events)
        );
        Player player = player();
        source.subscribeStateChanges(ignored -> {
            throw new IllegalStateException("test failure");
        });

        assertThatCode(() -> events.fireAuthenticated(player)).doesNotThrowAnyException();
    }

    @Test
    void neverInterceptsLibreLoginServerChoiceEvents() {
        FakeEventProvider events = new FakeEventProvider();
        LibreLoginAuthSource source = new LibreLoginAuthSource(
                plugin(authorization(new AtomicBoolean(true), new AtomicBoolean()), events)
        );
        source.subscribeStateChanges(ignored -> { });

        assertThat(events.hasServerChoiceSubscriptions()).isFalse();
        source.close();
        assertThat(events.unsubscribedExactConsumers).hasValue(3);
    }

    @Test
    void offlineAccountRoutingToAuthServerIsNeverSuppressedBySharedProtocolIdentity() {
        FakeEventProvider events = new FakeEventProvider();
        AtomicBoolean authorized = new AtomicBoolean(false);
        LibreLoginAuthSource source = new LibreLoginAuthSource(
                plugin(authorization(authorized, new AtomicBoolean()), events)
        );
        RegisteredServer auth = registeredServer("aero-auth");
        RegisteredServer gameplay = registeredServer("aeronautics");
        source.subscribeStateChanges(ignored -> { });

        assertThat(events.fireLimboServerChoose(player(gameplay), auth))
                .as("an offline account must leave the frozen gameplay backend for LibreLogin auth")
                .isSameAs(auth);

        authorized.set(true);
        assertThat(events.fireLobbyServerChoose(player(auth), gameplay))
                .as("an authenticated account must leave auth for the gameplay backend")
                .isSameAs(gameplay);
        source.close();
    }

    @SuppressWarnings("unchecked")
    private AuthorizationProvider<Player> authorization(
            AtomicBoolean authorized,
            AtomicBoolean awaitingTwoFactor
    ) {
        return proxy(AuthorizationProvider.class, (method, args) -> switch (method.getName()) {
            case "isAuthorized" -> authorized.get();
            case "isAwaiting2FA" -> awaitingTwoFactor.get();
            default -> defaultValue(method.getReturnType());
        });
    }

    @SuppressWarnings("unchecked")
    private LibreLoginPlugin<Player, RegisteredServer> plugin(
            AuthorizationProvider<Player> authorization,
            FakeEventProvider events
    ) {
        return proxy(LibreLoginPlugin.class, (method, args) -> switch (method.getName()) {
            case "getAuthorizationProvider" -> authorization;
            case "getEventProvider" -> events;
            case "getEventTypes" -> events.getTypes();
            default -> defaultValue(method.getReturnType());
        });
    }

    private Player player() {
        return player(null);
    }

    private Player player(RegisteredServer currentServer) {
        UUID uuid = UUID.randomUUID();
        ServerConnection connection = currentServer == null
                ? null
                : proxy(ServerConnection.class, (method, args) -> switch (method.getName()) {
                    case "getServer" -> currentServer;
                    case "getServerInfo" -> currentServer.getServerInfo();
                    default -> defaultValue(method.getReturnType());
                });
        return proxy(Player.class, (method, args) -> switch (method.getName()) {
            case "getUniqueId" -> uuid;
            case "getUsername" -> "Steve";
            case "getCurrentServer" -> Optional.ofNullable(connection);
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

    private static final class FakeEventProvider implements EventProvider<Player, RegisteredServer> {
        private final EventTypes<Player, RegisteredServer> types = EventProvider.super.getTypes();
        private Consumer<AuthenticatedEvent<Player, RegisteredServer>> authenticated;
        private Consumer<WrongPasswordEvent<Player, RegisteredServer>> wrongPassword;
        private Consumer<PremiumLoginSwitchEvent<Player, RegisteredServer>> premiumLoginSwitch;
        private Consumer<LobbyServerChooseEvent<Player, RegisteredServer>> lobbyServerChoose;
        private Consumer<LimboServerChooseEvent<Player, RegisteredServer>> limboServerChoose;
        private final AtomicInteger unsubscribedExactConsumers = new AtomicInteger();

        @Override
        public EventTypes<Player, RegisteredServer> getTypes() {
            return types;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <E extends Event<Player, RegisteredServer>> Consumer<E> subscribe(
                EventType<Player, RegisteredServer, E> type,
                Consumer<E> handler
        ) {
            if (type.equals(types.authenticated)) {
                authenticated = (Consumer<AuthenticatedEvent<Player, RegisteredServer>>) (Consumer<?>) handler;
            } else if (type.equals(types.wrongPassword)) {
                wrongPassword = (Consumer<WrongPasswordEvent<Player, RegisteredServer>>) (Consumer<?>) handler;
            } else if (type.equals(types.premiumLoginSwitch)) {
                premiumLoginSwitch =
                        (Consumer<PremiumLoginSwitchEvent<Player, RegisteredServer>>) (Consumer<?>) handler;
            } else if (type.equals(types.lobbyServerChoose)) {
                lobbyServerChoose =
                        (Consumer<LobbyServerChooseEvent<Player, RegisteredServer>>) (Consumer<?>) handler;
            } else if (type.equals(types.limboServerChoose)) {
                limboServerChoose =
                        (Consumer<LimboServerChooseEvent<Player, RegisteredServer>>) (Consumer<?>) handler;
            }
            return handler;
        }

        @Override
        public void unsubscribe(Consumer<? extends Event<Player, RegisteredServer>> handler) {
            if (authenticated == handler) {
                authenticated = null;
                unsubscribedExactConsumers.incrementAndGet();
            } else if (wrongPassword == handler) {
                wrongPassword = null;
                unsubscribedExactConsumers.incrementAndGet();
            } else if (premiumLoginSwitch == handler) {
                premiumLoginSwitch = null;
                unsubscribedExactConsumers.incrementAndGet();
            } else if (lobbyServerChoose == handler) {
                lobbyServerChoose = null;
                unsubscribedExactConsumers.incrementAndGet();
            } else if (limboServerChoose == handler) {
                limboServerChoose = null;
                unsubscribedExactConsumers.incrementAndGet();
            }
        }

        @Override
        public <E extends Event<Player, RegisteredServer>> void fire(
                EventType<Player, RegisteredServer, E> type,
                E event
        ) {
        }

        private boolean hasServerChoiceSubscriptions() {
            return lobbyServerChoose != null || limboServerChoose != null;
        }

        @SuppressWarnings("unchecked")
        private void fireAuthenticated(Player player) {
            AuthenticatedEvent<Player, RegisteredServer> event = proxy(
                    AuthenticatedEvent.class,
                    (method, args) -> switch (method.getName()) {
                        case "getPlayer" -> player;
                        case "getUUID" -> player.getUniqueId();
                        case "getReason" -> AuthenticatedEvent.AuthenticationReason.LOGIN;
                        default -> defaultValue(method.getReturnType());
                    }
            );
            authenticated.accept(event);
        }

        @SuppressWarnings("unchecked")
        private void fireWrongPassword(Player player) {
            WrongPasswordEvent<Player, RegisteredServer> event = proxy(
                    WrongPasswordEvent.class,
                    (method, args) -> switch (method.getName()) {
                        case "getPlayer" -> player;
                        case "getUUID" -> player.getUniqueId();
                        case "getSource" -> WrongPasswordEvent.AuthenticationSource.TOTP;
                        default -> defaultValue(method.getReturnType());
                    }
            );
            wrongPassword.accept(event);
        }

        @SuppressWarnings("unchecked")
        private void firePremiumLoginSwitch(Player player) {
            PremiumLoginSwitchEvent<Player, RegisteredServer> event = proxy(
                    PremiumLoginSwitchEvent.class,
                    (method, args) -> switch (method.getName()) {
                        case "getPlayer" -> player;
                        case "getUUID" -> player.getUniqueId();
                        default -> defaultValue(method.getReturnType());
                    }
            );
            premiumLoginSwitch.accept(event);
        }

        @SuppressWarnings("unchecked")
        private RegisteredServer fireLobbyServerChoose(
                Player player,
                RegisteredServer selectedServer
        ) {
            AtomicReference<RegisteredServer> selected = new AtomicReference<>(selectedServer);
            LobbyServerChooseEvent<Player, RegisteredServer> event = proxy(
                    LobbyServerChooseEvent.class,
                    (method, args) -> switch (method.getName()) {
                        case "getPlayer" -> player;
                        case "getUUID" -> player.getUniqueId();
                        case "getServer" -> selected.get();
                        case "setServer" -> {
                            selected.set((RegisteredServer) args[0]);
                            yield null;
                        }
                        case "isFallback" -> false;
                        default -> defaultValue(method.getReturnType());
                    }
            );
            if (lobbyServerChoose != null) {
                lobbyServerChoose.accept(event);
            }
            return selected.get();
        }

        @SuppressWarnings("unchecked")
        private RegisteredServer fireLimboServerChoose(
                Player player,
                RegisteredServer selectedServer
        ) {
            AtomicReference<RegisteredServer> selected = new AtomicReference<>(selectedServer);
            LimboServerChooseEvent<Player, RegisteredServer> event = proxy(
                    LimboServerChooseEvent.class,
                    (method, args) -> switch (method.getName()) {
                        case "getPlayer" -> player;
                        case "getUUID" -> player.getUniqueId();
                        case "getServer" -> selected.get();
                        case "setServer" -> {
                            selected.set((RegisteredServer) args[0]);
                            yield null;
                        }
                        default -> defaultValue(method.getReturnType());
                    }
            );
            if (limboServerChoose != null) {
                limboServerChoose.accept(event);
            }
            return selected.get();
        }
    }
}
