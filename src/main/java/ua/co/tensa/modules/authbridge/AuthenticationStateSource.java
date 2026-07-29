package ua.co.tensa.modules.authbridge;

import com.velocitypowered.api.proxy.Player;
import ua.co.tensa.authbridge.protocol.AuthBridgeState;

import java.util.function.Consumer;

interface AuthenticationStateSource extends AutoCloseable {
    AuthBridgeState currentState(Player player);

    void subscribeAuthenticated(Consumer<Player> listener);

    @Override
    void close();
}
