package ua.co.tensa.modules.authbridge;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.junit.jupiter.api.Test;
import ua.co.tensa.authbridge.protocol.AuthBridgeState;
import xyz.kyngs.librelogin.api.LibreLoginPlugin;
import xyz.kyngs.librelogin.api.authorization.AuthorizationProvider;
import xyz.kyngs.librelogin.api.event.Event;
import xyz.kyngs.librelogin.api.event.EventProvider;
import xyz.kyngs.librelogin.api.event.EventType;
import xyz.kyngs.librelogin.api.event.EventTypes;
import xyz.kyngs.librelogin.api.event.events.AuthenticatedEvent;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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

        source.subscribeAuthenticated(ignored -> callbacks.incrementAndGet());
        assertThat(source.currentState(player)).isEqualTo(AuthBridgeState.LOCKED);

        authorized.set(true);
        assertThat(source.currentState(player)).isEqualTo(AuthBridgeState.AUTHORIZED);

        awaitingTwoFactor.set(true);
        assertThat(source.currentState(player)).isEqualTo(AuthBridgeState.LOCKED);

        events.fireAuthenticated(player);
        assertThat(callbacks).hasValue(1);

        source.close();
        assertThat(events.unsubscribedExactConsumer).isTrue();
    }

    @Test
    void consumerFailureNeverEscapesIntoLibreLogin() {
        FakeEventProvider events = new FakeEventProvider();
        LibreLoginAuthSource source = new LibreLoginAuthSource(
                plugin(authorization(new AtomicBoolean(true), new AtomicBoolean()), events)
        );
        Player player = player();
        source.subscribeAuthenticated(ignored -> {
            throw new IllegalStateException("test failure");
        });

        assertThatCode(() -> events.fireAuthenticated(player)).doesNotThrowAnyException();
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
        UUID uuid = UUID.randomUUID();
        return proxy(Player.class, (method, args) -> switch (method.getName()) {
            case "getUniqueId" -> uuid;
            case "getUsername" -> "Steve";
            default -> defaultValue(method.getReturnType());
        });
    }

    private static final class FakeEventProvider implements EventProvider<Player, RegisteredServer> {
        private final EventTypes<Player, RegisteredServer> types = EventProvider.super.getTypes();
        private Consumer<AuthenticatedEvent<Player, RegisteredServer>> authenticated;
        private boolean unsubscribedExactConsumer;

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
            }
            return handler;
        }

        @Override
        public void unsubscribe(Consumer<? extends Event<Player, RegisteredServer>> handler) {
            unsubscribedExactConsumer = authenticated == handler;
            if (unsubscribedExactConsumer) {
                authenticated = null;
            }
        }

        @Override
        public <E extends Event<Player, RegisteredServer>> void fire(
                EventType<Player, RegisteredServer, E> type,
                E event
        ) {
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
    }
}
