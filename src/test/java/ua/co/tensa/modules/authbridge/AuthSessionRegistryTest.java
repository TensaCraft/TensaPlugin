package ua.co.tensa.modules.authbridge;

import com.velocitypowered.api.proxy.Player;
import org.junit.jupiter.api.Test;
import ua.co.tensa.authbridge.protocol.AuthBridgeState;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.co.tensa.modules.authbridge.AuthBridgeTestProxies.defaultValue;
import static ua.co.tensa.modules.authbridge.AuthBridgeTestProxies.proxy;

class AuthSessionRegistryTest {
    @Test
    void sessionsStartLockedAndUseIncreasingSequences() {
        UUID playerId = UUID.randomUUID();
        Player player = player("Steve", playerId);
        AuthSessionRegistry registry = new AuthSessionRegistry();

        AuthSessionRegistry.Snapshot initial = registry.begin(player);
        AuthSessionRegistry.Snapshot updated = registry.update(player, AuthBridgeState.AUTHORIZED);
        AuthSessionRegistry.Snapshot firstMessage = registry.next(player);
        AuthSessionRegistry.Snapshot secondMessage = registry.next(player);

        assertThat(initial.state()).isEqualTo(AuthBridgeState.LOCKED);
        assertThat(updated.sessionId()).isEqualTo(initial.sessionId());
        assertThat(firstMessage.state()).isEqualTo(AuthBridgeState.AUTHORIZED);
        assertThat(firstMessage.sequence()).isEqualTo(1L);
        assertThat(secondMessage.sequence()).isEqualTo(2L);
    }

    @Test
    void staleDisconnectCannotRemoveAReplacementConnection() {
        UUID playerId = UUID.randomUUID();
        Player oldConnection = player("Steve", playerId);
        Player newConnection = player("Steve", playerId);
        AuthSessionRegistry registry = new AuthSessionRegistry();

        registry.begin(oldConnection);
        AuthSessionRegistry.Snapshot replacement = registry.begin(newConnection);
        registry.remove(oldConnection);

        assertThat(registry.ensure(newConnection).sessionId()).isEqualTo(replacement.sessionId());
    }

    @Test
    void staleCallbacksCannotReplaceTheCurrentConnectionSession() {
        UUID playerId = UUID.randomUUID();
        Player oldConnection = player("Steve", playerId);
        Player newConnection = player("Steve", playerId);
        AuthSessionRegistry registry = new AuthSessionRegistry();

        registry.begin(oldConnection);
        AuthSessionRegistry.Snapshot replacement = registry.begin(newConnection);

        assertThat(registry.update(oldConnection, AuthBridgeState.AUTHORIZED)).isNull();
        assertThat(registry.next(oldConnection)).isNull();
        assertThat(registry.ensure(newConnection).sessionId()).isEqualTo(replacement.sessionId());
        assertThat(registry.ensure(newConnection).state()).isEqualTo(AuthBridgeState.LOCKED);
    }

    private Player player(String username, UUID uuid) {
        return proxy(Player.class, (method, args) -> switch (method.getName()) {
            case "getUsername" -> username;
            case "getUniqueId" -> uuid;
            default -> defaultValue(method.getReturnType());
        });
    }
}
