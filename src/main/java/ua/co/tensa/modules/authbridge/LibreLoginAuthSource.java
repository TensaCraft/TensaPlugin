package ua.co.tensa.modules.authbridge;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import ua.co.tensa.Message;
import ua.co.tensa.authbridge.protocol.AuthState;
import xyz.kyngs.librelogin.api.LibreLoginPlugin;
import xyz.kyngs.librelogin.api.event.Event;
import xyz.kyngs.librelogin.api.event.EventType;
import xyz.kyngs.librelogin.api.event.PlayerBasedEvent;
import xyz.kyngs.librelogin.api.provider.LibreLoginProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

final class LibreLoginAuthSource implements AuthenticationStateSource {
    private final LibreLoginPlugin<Player, RegisteredServer> libreLogin;
    private final List<Consumer<? extends Event<Player, RegisteredServer>>> subscriptions =
            new ArrayList<>();

    static LibreLoginAuthSource open(ProxyServer server) {
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
        return new LibreLoginAuthSource(plugin);
    }

    LibreLoginAuthSource(LibreLoginPlugin<Player, RegisteredServer> libreLogin) {
        this.libreLogin = Objects.requireNonNull(libreLogin, "libreLogin");
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

}
