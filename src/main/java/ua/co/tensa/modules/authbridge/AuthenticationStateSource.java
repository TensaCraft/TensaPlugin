package ua.co.tensa.modules.authbridge;

import com.velocitypowered.api.proxy.Player;
import ua.co.tensa.authbridge.protocol.AuthState;

import java.util.function.Consumer;

interface AuthenticationStateSource extends AutoCloseable {
    AuthState currentState(Player player);

    void subscribeStateChanges(Consumer<Player> listener);

    @Override
    void close();
}
