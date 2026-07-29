package ua.co.tensa.modules.authbridge;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import ua.co.tensa.Message;
import ua.co.tensa.authbridge.protocol.AuthBridgeState;
import xyz.kyngs.librelogin.api.LibreLoginPlugin;
import xyz.kyngs.librelogin.api.event.Event;
import xyz.kyngs.librelogin.api.event.events.AuthenticatedEvent;
import xyz.kyngs.librelogin.api.provider.LibreLoginProvider;

import java.util.Objects;
import java.util.function.Consumer;

final class LibreLoginAuthSource implements AuthenticationStateSource {
    private final LibreLoginPlugin<Player, RegisteredServer> libreLogin;
    private Consumer<AuthenticatedEvent<Player, RegisteredServer>> subscription;

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
    public AuthBridgeState currentState(Player player) {
        var authorization = libreLogin.getAuthorizationProvider();
        return authorization.isAuthorized(player) && !authorization.isAwaiting2FA(player)
                ? AuthBridgeState.AUTHORIZED
                : AuthBridgeState.LOCKED;
    }

    @Override
    public synchronized void subscribeAuthenticated(Consumer<Player> listener) {
        Objects.requireNonNull(listener, "listener");
        unsubscribe();
        subscription = event -> {
            try {
                Player player = event.getPlayer();
                if (player != null) {
                    listener.accept(player);
                }
            } catch (Throwable throwable) {
                Message.warn("LibreLogin auth bridge event failed: " + safeMessage(throwable));
            }
        };
        libreLogin.getEventProvider().subscribe(
                libreLogin.getEventTypes().authenticated,
                subscription
        );
    }

    @Override
    public synchronized void close() {
        unsubscribe();
    }

    private void unsubscribe() {
        if (subscription == null) {
            return;
        }
        try {
            Consumer<? extends Event<Player, RegisteredServer>> current = subscription;
            libreLogin.getEventProvider().unsubscribe(current);
        } catch (Throwable throwable) {
            Message.warn("LibreLogin auth bridge unsubscribe failed: " + safeMessage(throwable));
        } finally {
            subscription = null;
        }
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }
}
